//! Pure GLM response shape and Markdown projection, shared by probes and OCR.
use serde_json::Value;

fn part(value: &Value) -> Option<&str> {
    match value {
        Value::String(text) => Some(text),
        Value::Object(map) => map.get("markdown").or_else(|| map.get("content")).and_then(Value::as_str),
        _ => None,
    }
}

pub(crate) fn parts(value: &Value) -> Option<Vec<&str>> {
    match value.get("md_results")? {
        Value::String(text) => Some(vec![text]),
        Value::Object(_) => part(&value["md_results"]).map(|text| vec![text]),
        Value::Array(items) => {
            let parts: Vec<_> = items.iter().filter_map(part).collect();
            (items.is_empty() || !parts.is_empty()).then_some(parts)
        }
        _ => None,
    }
}

pub(crate) fn markdown(value: &Value) -> String {
    parts(value).unwrap_or_default().join("\n\n")
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn strings_keep_exact_authored_markdown() {
        assert_eq!(markdown(&json!({"md_results":"# 原文\r\n  空格\n😀"})), "# 原文\r\n  空格\n😀");
    }

    #[test]
    fn mixed_parts_keep_existing_order_and_field_precedence() {
        let value=json!({"md_results":["第一页",{"markdown":"第二页"},{"content":"第三页"},null,{"markdown":null,"content":"不能猜配"}]});
        assert_eq!(markdown(&value),"第一页\n\n第二页\n\n第三页");
        assert_eq!(parts(&value).unwrap(),vec!["第一页","第二页","第三页"]);
    }

    #[test]
    fn objects_keep_markdown_priority_without_falling_through_invalid_values() {
        assert_eq!(markdown(&json!({"md_results":{"content":"原文"}})),"原文");
        assert_eq!(markdown(&json!({"md_results":{"markdown":"优先","content":"另一字段"}})),"优先");
        assert!(parts(&json!({"md_results":{"markdown":null,"content":"不能替代"}})).is_none());
    }

    #[test]
    fn an_explicit_empty_result_has_a_shape_without_becoming_recognized_text() {
        for value in [json!({"md_results":""}),json!({"md_results":[]}),json!({"md_results":{"markdown":""}})] {
            assert!(parts(&value).is_some());assert_eq!(markdown(&value),"");
        }
        for value in [json!({}),json!({"md_results":null}),json!({"md_results":[null,{}]})] {
            assert!(parts(&value).is_none());assert_eq!(markdown(&value),"");
        }
    }
}
