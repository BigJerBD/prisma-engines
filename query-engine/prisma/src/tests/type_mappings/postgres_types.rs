use super::test_api::*;
use indoc::indoc;
use test_macros::test_each_connector;

const CREATE_TYPES_TABLE: &str = indoc! {
    r##"
    CREATE TABLE "public"."types" (
        id SERIAL PRIMARY KEY,
        numeric_int2 int2,
        numeric_int4 int4,
        numeric_int8 int8,

        numeric_decimal decimal(5,5),
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

        /* Timestamp without time zone */
        time_timestamp timestamp,
        /* Timestamp with time zone */
        time_timestamptz timestamptz,
        time_date date,
        /* Time without time zone */
        time_time time,
        /* Time with time zone */
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

#[test_each_connector(tags("postgres"))]
async fn postgres_types_roundtrip(api: &TestApi) -> TestResult {
    api.execute(CREATE_TYPES_TABLE).await?;

    todo!();
    Ok(())
}
