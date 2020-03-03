use super::*;
use crate::{
    query_document::{ParsedArgument, ParsedInputMap},
    QueryGraphBuilderError, QueryGraphBuilderResult,
};
use connector::QueryArguments;
use prisma_models::{Field, ModelRef, PrismaValue, RecordProjection};
use std::convert::TryInto;

/// Expects the caller to know that it is structurally guaranteed that query arguments can be extracted,
/// e.g. that the query schema guarantees that required fields are present.
/// Errors occur if conversions fail unexpectedly.
pub fn extract_query_args(arguments: Vec<ParsedArgument>, model: &ModelRef) -> QueryGraphBuilderResult<QueryArguments> {
    arguments
        .into_iter()
        .fold(Ok(QueryArguments::default()), |result, arg| {
            if let Ok(res) = result {
                match arg.name.as_str() {
                    "skip" => Ok(QueryArguments {
                        skip: arg.value.try_into()?,
                        ..res
                    }),

                    "first" => Ok(QueryArguments {
                        first: arg.value.try_into()?,
                        ..res
                    }),

                    "last" => Ok(QueryArguments {
                        last: arg.value.try_into()?,
                        ..res
                    }),

                    "after" => Ok(QueryArguments {
                        after: extract_cursor(arg.value, model)?,
                        ..res
                    }),

                    "before" => Ok(QueryArguments {
                        before: extract_cursor(arg.value, model)?,
                        ..res
                    }),

                    "orderBy" => Ok(QueryArguments {
                        order_by: Some(arg.value.try_into()?),
                        ..res
                    }),

                    "where" => {
                        let val: Option<ParsedInputMap> = arg.value.try_into()?;
                        match val {
                            Some(m) => {
                                let filter = Some(extract_filter(m, model, true)?);
                                Ok(QueryArguments { filter, ..res })
                            }
                            None => Ok(res),
                        }
                    }

                    _ => Ok(res),
                }
            } else {
                result
            }
        })
}

fn extract_cursor(value: ParsedInputValue, model: &ModelRef) -> QueryGraphBuilderResult<Option<RecordProjection>> {
    if let Err(_) = value.assert_non_null() {
        return Ok(None);
    }

    // map.assert_size(1)?;
    // let (field_name, value): (String, ParsedInputValue) = map.into_iter().nth(0).unwrap();

    let input_map: ParsedInputMap = value.try_into()?;
    let mut pairs = vec![];

    for (field_name, map_value) in input_map {
        // Always try to resolve regular fields first. If that fails, try to resolve compound fields.
        let model_fields = model
            .fields()
            .find_from_all(&field_name)
            .map(|f| vec![f.clone()])
            // .map_err(|err| err.into())
            .or_else(|_| {
                utils::resolve_compound_field(&field_name, &model).ok_or(QueryGraphBuilderError::AssertionError(
                    format!(
                        "Unable to resolve field {} to a field or a set of fields on model {}",
                        field_name, model.name
                    ),
                ))
            })?;

        if model_fields.len() == 1
            && model_fields
                .first()
                .map(|f| f.data_source_fields().len() == 1)
                .unwrap_or(false)
        {
            // Single field to single underlying data source field case.
            let field = model_fields.first().unwrap();
            let dsf = field.data_source_fields().pop().unwrap();
            let value: PrismaValue = map_value.try_into()?;

            pairs.push((dsf, value));
        } else {
            // Compound / relation field with > 1 db fields case.
            let mut compound_map: ParsedInputMap = map_value.try_into()?;

            for field in model_fields {
                // Unwrap is safe because validation guarantees that the value is present.
                // let value = compound_map.remove(&field.name()).unwrap().try_into()?;
                // pairs.push((field, value));

                // Relation and scalar fields are different in the way their underlying fields in the map are named:
                // scalar: has actual model field names in the compound map.
                // relation: has data source field names in the compound map for lack of a better mapping.
            }

            todo!()
        }

        // .and_then(|fields| {
        //
        //     let mut result = vec![];
        //     let mut compound_map: ParsedInputMap = map_value.try_into()?;

        //     Ok(Some(result))
        // })

        // match model_field {
        //     Field::Scalar(sf) => {
        //         let value: PrismaValue = value.try_into()?;

        //         Ok(Some(RecordProjection::new(vec![(
        //             sf.data_source_field().clone(),
        //             value,
        //         )])))
        //     }

        //     Field::Relation(rf) => {
        //         let fields = rf.data_source_fields();

        //         if fields.len() == 1 {
        //             let value: PrismaValue = value.try_into()?;

        //             Ok(Some(RecordProjection::new(vec![(
        //                 fields.first().unwrap().clone(),
        //                 value,
        //             )])))
        //         } else {
        //             let mut map: ParsedInputMap = value.try_into()?;

        //             let pairs: Vec<_> = fields
        //                 .into_iter()
        //                 .map(|field| {
        //                     // Every field in the map must correspond to
        //                     // If a field is not present, nulls are inserted.

        //                     map.remove()

        //                     todo!()
        //                 })
        //                 .collect();

        //             Ok(Some(RecordProjection::new(pairs)))
        //         }
        //     }
        // }
    }

    Ok(Some(RecordProjection::new(pairs)))
}
