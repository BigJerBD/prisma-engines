use crate::{DataSourceFieldRef, DomainError, ModelProjection, PrismaValue, RecordProjection};

#[derive(Debug, Clone)]
pub struct SingleRecord {
    pub record: Record,
    pub field_names: Vec<String>,
}

impl Into<ManyRecords> for SingleRecord {
    fn into(self) -> ManyRecords {
        ManyRecords {
            records: vec![self.record],
            field_names: self.field_names,
        }
    }
}

impl SingleRecord {
    pub fn new(record: Record, field_names: Vec<String>) -> Self {
        Self { record, field_names }
    }

    pub fn projection(&self, projection: &ModelProjection) -> crate::Result<RecordProjection> {
        self.record.projection(&self.field_names, projection)
    }

    pub fn get_field_value(&self, field: &str) -> crate::Result<&PrismaValue> {
        self.record.get_field_value(&self.field_names, field)
    }
}

#[derive(Debug, Clone, Default)]
pub struct ManyRecords {
    pub records: Vec<Record>,
    pub field_names: Vec<String>,
}

impl ManyRecords {
    pub fn projections(&self, model_projection: &ModelProjection) -> crate::Result<Vec<RecordProjection>> {
        self.records
            .iter()
            .map(|record| {
                record
                    .projection(&self.field_names, model_projection)
                    .map(|i| i.clone())
            })
            .collect()
    }

    /// Maps into a Vector of (field_name, value) tuples
    pub fn as_pairs(&self) -> Vec<Vec<(String, PrismaValue)>> {
        self.records
            .iter()
            .map(|record| {
                record
                    .values
                    .iter()
                    .zip(self.field_names.iter())
                    .map(|(value, name)| (name.clone(), value.clone()))
                    .collect()
            })
            .collect()
    }

    /// Reverses the wrapped records in place
    pub fn reverse(&mut self) {
        self.records.reverse();
    }
}

#[derive(Debug, Default, Clone)]
pub struct Record {
    pub values: Vec<PrismaValue>,
    pub parent_id: Option<RecordProjection>,
}

impl Record {
    pub fn new(values: Vec<PrismaValue>) -> Record {
        Record {
            values,
            ..Default::default()
        }
    }

    pub fn projection(
        &self,
        field_names: &[String],
        model_projection: &ModelProjection,
    ) -> crate::Result<RecordProjection> {
        let pairs: Vec<(DataSourceFieldRef, PrismaValue)> = model_projection
            .fields()
            .into_iter()
            .flat_map(|field| {
                let source_fields = field.data_source_fields();

                source_fields.into_iter().map(|source_field| {
                    self.get_field_value(field_names, &source_field.name)
                        .map(|val| (source_field, val.clone()))
                })
            })
            .collect::<crate::Result<Vec<_>>>()?;

        Ok(RecordProjection { pairs })
    }

    pub fn identifying_values(
        &self,
        field_names: &[String],
        model_projection: &ModelProjection,
    ) -> crate::Result<Vec<&PrismaValue>> {
        let x: Vec<&PrismaValue> = model_projection
            .fields()
            .into_iter()
            .flat_map(|field| {
                let source_fields = field.data_source_fields();

                source_fields
                    .into_iter()
                    .map(|source_field| self.get_field_value(field_names, &source_field.name))
            })
            .collect::<crate::Result<Vec<_>>>()?;

        Ok(x)
    }

    pub fn get_field_value(&self, field_names: &[String], field: &str) -> crate::Result<&PrismaValue> {
        let index = field_names.iter().position(|r| r == field).map(Ok).unwrap_or_else(|| {
            Err(DomainError::FieldNotFound {
                name: field.to_string(),
                model: format!(
                    "Field not found in record {:?}. Field names are: {:?}, looking for: {:?}",
                    &self, &field_names, field
                ),
            })
        })?;

        Ok(&self.values[index])
    }

    pub fn set_parent_id(&mut self, parent_id: RecordProjection) {
        self.parent_id = Some(parent_id);
    }
}
