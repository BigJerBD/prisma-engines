use super::test_api::*;
use datamodel::ScalarType;
use indoc::indoc;
use pretty_assertions::assert_eq;
use serde_json::json;
use test_macros::test_each_connector;

const CREATE_TYPES_TABLE: &str = indoc! {
    r##"
    CREATE TABLE "prisma-tests"."types" (
        id SERIAL PRIMARY KEY,
        numeric_int2 int2,
        numeric_int4 int4,
        numeric_int8 int8,

        numeric_decimal decimal(8, 4),
        numeric_float4 float4,
        numeric_float8 float8,

        numeric_serial2 serial2,
        numeric_serial4 serial4,
        numeric_serial8 serial8,

        numeric_money money,
        numeric_oid oid,

        string_char char(8),
        string_varchar varchar(20),
        string_text text,

        binary_bytea bytea,
        binary_bits  bit(80),
        binary_bits_varying bit varying(80),
        binary_uuid uuid,

        time_timestamp timestamp,
        time_timestamptz timestamptz,
        time_date date,
        time_time time,
        time_timetz timetz,
        time_interval interval,

        boolean_boolean boolean,

        network_cidr cidr,
        network_inet inet,
        network_mac  macaddr,

        search_tsvector tsvector,
        search_tsquery tsquery,

        json_json json,
        json_jsonb jsonb,

        range_int4range int4range,
        range_int8range int8range,
        range_numrange numrange,
        range_tsrange tsrange,
        range_tstzrange tstzrange,
        range_daterange daterange
    );
    "##
};

#[test_each_connector(tags("postgres"), log = "debug")]
async fn postgres_types_roundtrip(api: &TestApi) -> TestResult {
    api.execute(CREATE_TYPES_TABLE).await?;

    let (datamodel, engine) = api.create_engine().await?;

    datamodel.assert_model("types", |model| {
        model
            .assert_field_type("numeric_int2", ScalarType::Int)?
            .assert_field_type("numeric_int4", ScalarType::Int)?
            .assert_field_type("numeric_int8", ScalarType::Int)?
            .assert_field_type("numeric_decimal", ScalarType::Float)?
            .assert_field_type("numeric_float4", ScalarType::Float)?
            .assert_field_type("numeric_float8", ScalarType::Float)?
            .assert_field_type("numeric_serial2", ScalarType::Int)?
            .assert_field_type("numeric_serial4", ScalarType::Int)?
            .assert_field_type("numeric_serial8", ScalarType::Int)?
            .assert_field_type("numeric_money", ScalarType::Float)?
            .assert_field_type("numeric_oid", ScalarType::Int)?
            .assert_field_type("string_char", ScalarType::String)?
            .assert_field_type("string_varchar", ScalarType::String)?
            .assert_field_type("string_text", ScalarType::String)?
            .assert_field_type("binary_bytea", ScalarType::String)?
            .assert_field_type("binary_bits", ScalarType::String)?
            .assert_field_type("binary_bits_varying", ScalarType::String)?
            .assert_field_type("binary_uuid", ScalarType::String)?
            .assert_field_type("time_timestamp", ScalarType::DateTime)?
            .assert_field_type("time_timestamptz", ScalarType::DateTime)?
            .assert_field_type("time_date", ScalarType::DateTime)?
            .assert_field_type("time_time", ScalarType::DateTime)?
            .assert_field_type("time_interval", ScalarType::String)?
            .assert_field_type("boolean_boolean", ScalarType::Boolean)?
            .assert_field_type("network_cidr", ScalarType::String)?
            .assert_field_type("network_inet", ScalarType::String)?
            .assert_field_type("network_mac", ScalarType::String)?
            .assert_field_type("search_tsvector", ScalarType::String)?
            .assert_field_type("search_tsquery", ScalarType::String)?
            .assert_field_type("json_json", ScalarType::String)?
            .assert_field_type("json_jsonb", ScalarType::String)?
            .assert_field_type("range_int4range", ScalarType::String)?
            .assert_field_type("range_int8range", ScalarType::String)?
            .assert_field_type("range_numrange", ScalarType::String)?
            .assert_field_type("range_tsrange", ScalarType::String)?
            .assert_field_type("range_tstzrange", ScalarType::String)?
            .assert_field_type("range_daterange", ScalarType::String)
    })?;

    let query = indoc! {
        r##"
        mutation {
            createOnetypes(
                data: {
                    numeric_int2: 12
                    numeric_int4: 9002
                    numeric_int8: 100000000
                    numeric_decimal: 49.3444
                    numeric_float4: 12.12
                    numeric_float8: 3.139428
                    numeric_serial2: 8,
                    numeric_serial4: 80,
                    numeric_serial8: 80000,
                    # numeric_money: 3.50
                    numeric_oid: 2000
                    string_char: "yeet"
                    string_varchar: "yeet variable"
                    string_text: "to yeet or not to yeet"
                    binary_uuid: "111142ec-880b-4062-913d-8eac479ab957"
                    time_timestamp: "2020-03-02T08:00:00.000"
                    time_timestamptz: "2020-03-02T08:00:00.000"
                    time_date: "2020-03-05T00:00:00.000"
                    time_time: "2020-03-05T08:00:00.000"
                    # time_interval: "3 hours"
                    boolean_boolean: true
                    # network_cidr: "192.168.100.14/24"
                    network_inet: "192.168.100.14"
                    # network_mac: "12:33:ed:44:49:36"
                    # search_tsvector: "''a'' ''dump'' ''dumps'' ''fox'' ''in'' ''the''"
                    # search_tsquery: "''foxy cat''"
                    json_json: "{ \"isJson\": true }"
                    json_jsonb: "{ \"isJSONB\": true }"
                    # range_int4range: "[-4, 8)"
                    # range_int8range: "[4000, 9000)"
                    # range_numrange: "[11.1, 22.2)"
                    # range_tsrange: "[2010-01-01 14:30, 2010-01-01 15:30)"
                    # range_tstzrange: "[2010-01-01 14:30, 2010-01-01 15:30)"
                    # range_daterange: "[2020-03-02, 2020-03-22)"
                }
            ) {
                numeric_int2
                numeric_int4
                numeric_int8
                numeric_decimal
                numeric_float4
                numeric_float8
                numeric_serial2
                numeric_serial4
                numeric_serial8
                # numeric_money
                numeric_oid
                string_char
                string_varchar
                string_text
                binary_uuid
                time_timestamp
                time_timestamptz
                time_date
                time_time
                # time_interval
                boolean_boolean
                # network_cidr
                network_inet
                # network_mac
                # search_tsvector
                # search_tsquery
                json_json
                json_jsonb
                # range_int4range
                # range_int8range
                # range_numrange
                # range_tsrange
                # range_tstzrange
                # range_daterange
            }
        }
        "##
    };

    let response = engine.request(query).await;

    let expected_response = json!({
        "data": {
            "createOnetypes": {
                "numeric_int2": 12,
                "numeric_int4": 9002,
                "numeric_int8": 100000000,
                "numeric_serial2": 8,
                "numeric_serial4": 80,
                "numeric_serial8": 80000,
                "numeric_decimal": 49.3444,
                "numeric_float4": 12.12,
                "numeric_float8": 3.139428,
                // "numeric_money": 3.5,
                "numeric_oid": 2000,
                "string_char": "yeet    ",
                "string_varchar": "yeet variable",
                "string_text": "to yeet or not to yeet",
                "binary_uuid": "111142ec-880b-4062-913d-8eac479ab957",
                "time_timestamp": "2020-03-02T08:00:00.000Z",
                "time_timestamptz": "2020-03-02T08:00:00.000Z",
                "time_date": "2020-03-05T00:00:00.000Z",
                "time_time": "1970-01-01T08:00:00.000Z",
                "boolean_boolean": true,
                "network_inet": "192.168.100.14",
                "json_json": "{\"isJson\":true}",
                "json_jsonb": "{\"isJSONB\":true}",
            }
        }
    });

    assert_eq!(response, expected_response);

    Ok(())
}
