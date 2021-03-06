use super::DataSourceField;
use crate::prelude::*;
use datamodel::{DefaultValue, FieldArity};
use once_cell::sync::OnceCell;
use std::{
    hash::{Hash, Hasher},
    sync::{Arc, Weak},
};

static ID_FIELD: &str = "id";
static EMBEDDED_ID_FIELD: &str = "_id";
static UPDATED_AT_FIELD: &str = "updatedAt";
static CREATED_AT_FIELD: &str = "createdAt";

pub type ScalarFieldRef = Arc<ScalarField>;
pub type ScalarFieldWeak = Weak<ScalarField>;

#[derive(Debug)]
pub struct ScalarFieldTemplate {
    pub name: String,
    pub type_identifier: TypeIdentifier,
    pub is_required: bool,
    pub is_list: bool,
    pub is_unique: bool,
    pub is_id: bool,
    pub is_auto_generated_int_id: bool,
    pub behaviour: Option<FieldBehaviour>,
    pub internal_enum: Option<InternalEnum>,
    pub data_source_field: dml::DataSourceField,
}

#[derive(DebugStub)]
pub struct ScalarField {
    pub name: String,
    pub type_identifier: TypeIdentifier,
    pub is_required: bool,
    pub is_list: bool,
    pub is_id: bool,
    pub is_auto_generated_int_id: bool,
    pub internal_enum: Option<InternalEnum>,
    pub behaviour: Option<FieldBehaviour>,

    #[debug_stub = "#ModelWeakRef#"]
    pub model: ModelWeakRef,
    pub(crate) is_unique: bool,
    pub(crate) data_source_field: OnceCell<DataSourceFieldRef>,
}

impl Eq for ScalarField {}

impl Hash for ScalarField {
    fn hash<H: Hasher>(&self, state: &mut H) {
        self.name.hash(state);
        self.type_identifier.hash(state);
        self.is_required.hash(state);
        self.is_list.hash(state);
        self.is_id.hash(state);
        self.is_auto_generated_int_id.hash(state);
        self.internal_enum.hash(state);
        self.behaviour.hash(state);
        self.is_unique.hash(state);
        self.model().hash(state);
    }
}

impl PartialEq for ScalarField {
    fn eq(&self, other: &Self) -> bool {
        self.name == other.name
            && self.type_identifier == other.type_identifier
            && self.is_required == other.is_required
            && self.is_list == other.is_list
            && self.is_id == other.is_id
            && self.is_auto_generated_int_id == other.is_auto_generated_int_id
            && self.internal_enum == other.internal_enum
            && self.behaviour == other.behaviour
            && self.default_value() == other.default_value()
            && self.is_unique == other.is_unique
            && self.model() == other.model()
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub enum FieldBehaviour {
    CreatedAt,
    UpdatedAt,
    ScalarList { strategy: ScalarListStrategy },
}

#[derive(Debug, Clone, Copy, Eq, PartialEq, Hash)]
pub enum ScalarListStrategy {
    Embedded,
    Relation,
}

impl ScalarFieldTemplate {
    pub fn build(self, model: ModelWeakRef) -> ScalarFieldRef {
        let scalar = ScalarField {
            name: self.name,
            type_identifier: self.type_identifier,
            is_id: self.is_id,
            is_required: self.is_required,
            is_list: self.is_list,
            is_auto_generated_int_id: self.is_auto_generated_int_id,
            is_unique: self.is_unique,
            internal_enum: self.internal_enum,
            behaviour: self.behaviour,
            model,
            data_source_field: OnceCell::new(),
        };

        let arc = Arc::new(scalar);
        arc.data_source_field
            .set(Arc::new(DataSourceField::new(
                self.data_source_field,
                FieldWeak::from(&arc),
            )))
            .unwrap();

        arc
    }
}

impl ScalarField {
    pub fn model(&self) -> ModelRef {
        self.model
            .upgrade()
            .expect("Model does not exist anymore. Parent model got deleted without deleting the child.")
    }

    pub fn internal_data_model(&self) -> InternalDataModelRef {
        self.model().internal_data_model()
    }

    /// A field is an ID field if the name is `id` or `_id` in legacy internal_data_models,
    /// or if the field has Id behaviour defined.
    pub fn is_id(&self) -> bool {
        if self.model().is_legacy() {
            self.name == ID_FIELD || self.name == EMBEDDED_ID_FIELD
        } else {
            self.is_id
        }
    }

    pub fn is_created_at(&self) -> bool {
        if self.model().is_legacy() {
            self.name == CREATED_AT_FIELD
        } else {
            match self.behaviour {
                Some(FieldBehaviour::CreatedAt) => true,
                _ => false,
            }
        }
    }

    pub fn is_updated_at(&self) -> bool {
        if self.model().is_legacy() {
            self.name == UPDATED_AT_FIELD
        } else {
            match self.behaviour {
                Some(FieldBehaviour::UpdatedAt) => true,
                _ => false,
            }
        }
    }

    pub fn unique(&self) -> bool {
        self.is_unique || self.is_id()
    }

    pub fn db_name(&self) -> &str {
        &self.data_source_field().name
    }

    pub fn type_identifier_with_arity(&self) -> (TypeIdentifier, FieldArity) {
        (
            self.data_source_field().field_type.into(),
            self.data_source_field().arity,
        )
    }

    pub fn default_value(&self) -> Option<&DefaultValue> {
        self.data_source_field().default_value.as_ref()
    }

    pub fn data_source_field(&self) -> &DataSourceFieldRef {
        self.data_source_field
            .get()
            .ok_or_else(|| String::from("Data source field must be set!"))
            .unwrap()
    }
}
