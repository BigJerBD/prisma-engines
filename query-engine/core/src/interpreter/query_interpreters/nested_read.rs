use crate::interpreter::query_interpreters::nested_pagination::NestedPagination;
use crate::{interpreter::InterpretationResult, query_ast::*};
use connector::{self, filter::Filter, ConnectionLike, IdFilter, QueryArguments, ReadOperations, ScalarCompare};
use prisma_models::{ManyRecords, RecordProjection, RelationFieldRef, Result as DomainResult, SelectedFields};
use prisma_value::PrismaValue;
use std::collections::HashMap;

pub async fn m2m<'a, 'b>(
    tx: &'a ConnectionLike<'a, 'b>,
    query: &RelatedRecordsQuery,
    parent_result: Option<&'a ManyRecords>,
    paginator: NestedPagination,
) -> InterpretationResult<ManyRecords> {
    let parent_field = &query.parent_field;
    let child_link_id = parent_field.related_field().linking_fields();

    // We know that in a m2m scenario, we always require the ID of the parent, nothing else.
    let parent_ids = match query.parent_projections {
        Some(ref links) => links.clone(),
        None => {
            let parent_model_id = query.parent_field.model().primary_identifier();
            parent_result
                .expect("[ID retrieval] No parent results present in the query graph for reading related records.")
                .projections(&parent_model_id)?
        }
    };

    let ids = tx.get_related_m2m_record_ids(&query.parent_field, &parent_ids).await?;
    let child_model_id = query.parent_field.related_model().primary_identifier();
    let child_ids: Vec<RecordProjection> = ids
        .iter()
        .map(|ri| child_model_id.assimilate(ri.1.clone()))
        .collect::<std::result::Result<Vec<_>, _>>()?;

    let is_compound_case = child_link_id.db_len() > 1;

    let args = if is_compound_case {
        let mut args = query.args.clone();
        let filter = child_ids.filter();

        args.filter = match args.filter {
            Some(existing_filter) => Some(Filter::and(vec![existing_filter, filter])),
            None => Some(filter),
        };

        args
    } else {
        // We can optimize queries with a single link ID field with IN filters.
        let child_field = child_link_id.data_source_fields().next().unwrap();
        let parent_links_as_prisma_values: Vec<PrismaValue> = child_ids
            .into_iter()
            .map(|vals| {
                vals.pairs.into_iter().next().unwrap().1
            })
            .collect();

        let filter = child_field.is_in(parent_links_as_prisma_values);
        let mut args = query.args.clone();

        args.filter = match args.filter {
            Some(existing_filter) => Some(Filter::and(vec![existing_filter, filter])),
            None => Some(filter),
        };

        args
    };

    let mut scalars = tx
        .get_many_records(
            &query.parent_field.related_model(),
            args,
            &query.selected_fields.only_scalar_and_inlined(),
        )
        .await?;

    // Child id to parent ids
    let mut id_map: HashMap<RecordProjection, Vec<RecordProjection>> = HashMap::new();

    for (parent_id, child_id) in ids {
        match id_map.get_mut(&child_id) {
            Some(v) => v.push(parent_id),
            None => {
                id_map.insert(child_id, vec![parent_id]);
            }
        };
    }

    let fields = &scalars.field_names;
    let mut additional_records = vec![];

    for record in scalars.records.iter_mut() {
        let record_id = record.projection(fields, &child_model_id)?;
        let mut parent_ids = id_map.remove(&record_id).expect("1");
        let first = parent_ids.pop().expect("2");

        record.parent_id = Some(first);

        for parent_id in parent_ids {
            let mut record = record.clone();

            record.parent_id = Some(parent_id);
            additional_records.push(record);
        }
    }

    scalars.records.extend(additional_records);
    paginator.apply_pagination(&mut scalars);

    Ok(scalars)
}

// [DTODO] This is implemented in an inefficient fashion, e.g. too much Arc cloning going on.
pub async fn one2m<'a, 'b>(
    tx: &'a ConnectionLike<'a, 'b>,
    parent_field: &RelationFieldRef,
    parent_projections: Option<Vec<RecordProjection>>,
    parent_result: Option<&'a ManyRecords>,
    query_args: QueryArguments,
    selected_fields: &SelectedFields,
    paginator: NestedPagination,
) -> InterpretationResult<ManyRecords> {
    let parent_model_id = parent_field.model().primary_identifier();
    let parent_link_id = parent_field.linking_fields();
    let child_link_id = parent_field.related_field().linking_fields();

    // Primary ID to link ID
    let joined_projections = match parent_projections {
        Some(projections) => projections,
        None => {
            let extractor = parent_model_id.clone().merge(parent_link_id.clone());
            parent_result
                .expect("[ID retrieval] No parent results present in the query graph for reading related records.")
                .projections(&extractor)?
        }
    };

    // Maps the identifying link values to all primary IDs they are tied to.
    // Only the values are hashed for easier comparison.
    let mut link_mapping: HashMap<Vec<PrismaValue>, Vec<RecordProjection>> = HashMap::new();
    let idents = vec![parent_model_id, parent_link_id];

    for projection in joined_projections {
        let mut split = projection.split_into(&idents);
        let link_id = split.pop().unwrap();
        let id = split.pop().unwrap();
        let link_values: Vec<PrismaValue> = link_id.pairs.into_iter().map(|(_, v)| v).collect();

        match link_mapping.get_mut(&link_values) {
            Some(records) => records.push(id),
            None => {
                let mut ids = Vec::new();
                ids.push(id);
                link_mapping.insert(link_values, ids);
            }
        }
    }

    let is_compound_case = child_link_id.db_len() > 1;
    let args = if is_compound_case {
        let filters: Vec<Filter> = link_mapping
            .keys()
            .into_iter()
            .map(|id_values: &Vec<PrismaValue>| {
                Ok(child_link_id
                    .from_unchecked(id_values.iter().map(|v: &PrismaValue| v.clone()).collect())
                    .filter())
            })
            .collect::<DomainResult<_>>()?;

        let filter = Filter::or(filters);
        let mut args = query_args;

        args.filter = match args.filter {
            Some(existing_filter) => Some(Filter::and(vec![existing_filter, filter])),
            None => Some(filter),
        };

        args
    } else {
        // We can optimize queries with a single link ID field with IN filters.
        let child_field = child_link_id.data_source_fields().next().unwrap();
        let parent_links_as_prisma_values: Vec<PrismaValue> = link_mapping
            .keys()
            .map(|vals| (*vals.first().unwrap()).clone())
            .collect();

        let filter = child_field.is_in(parent_links_as_prisma_values);
        let mut args = query_args;

        args.filter = match args.filter {
            Some(existing_filter) => Some(Filter::and(vec![existing_filter, filter])),
            None => Some(filter),
        };

        args
    };

    let mut scalars = tx
        .get_many_records(&parent_field.related_model(), args, selected_fields)
        .await?;

    let child_field_names = scalars.field_names.clone();

    // Inlining is done on the parent, this means that we need to write the primary parent ID
    // into the child records that we retrieved. The matching is done based on the parent link values.
    if parent_field.is_inlined_on_enclosing_model() {
        let mut additional_records = vec![];

        for mut record in scalars.records.iter_mut() {
            let child_link: RecordProjection = record.projection(&child_field_names, &child_link_id)?;
            let child_link_values: Vec<PrismaValue> = child_link.pairs.iter().map(|(_, v)| v.clone()).collect();

            if let Some(parent_ids) = link_mapping.get_mut(&child_link_values) {
                parent_ids.reverse();

                let parent_id = parent_ids.pop().unwrap();
                record.parent_id = Some(parent_id.clone());

                for parent_id in parent_ids {
                    let mut record = record.clone();

                    record.parent_id = Some((*parent_id).clone());
                    additional_records.push(record);
                }
            }
        }

        scalars.records.extend(additional_records);
    } else if parent_field.related_field().is_inlined_on_enclosing_model() {
        // Parent to map is inlined on the child records
        let child_link_fields = parent_field.related_field().linking_fields();

        for record in scalars.records.iter_mut() {
            let child_link: RecordProjection = record.projection(&child_field_names, &child_link_fields)?;
            let child_link_values: Vec<PrismaValue> = child_link.pairs.iter().map(|(_, v)| v.clone()).collect();

            if let Some(parent_ids) = link_mapping.get_mut(&child_link_values) {
                parent_ids.reverse();

                let parent_id = parent_ids.first().unwrap();
                record.parent_id = Some(parent_id.clone());
            }
        }
    } else {
        panic!(format!(
            "parent result: {:?}, relation: {:?}",
            &parent_result,
            &parent_field.relation()
        ));
    }

    paginator.apply_pagination(&mut scalars);
    Ok(scalars)
}
