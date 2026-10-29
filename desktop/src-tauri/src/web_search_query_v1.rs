//! Shared bounded query splitting, extracted from the pinned search service owner.
pub fn queries(clue:&str)->Result<Vec<String>, &'static str> {
    if clue.trim().is_empty() {return Err("SEARCH_QUERY_EMPTY");}
    if clue.chars().count()<=70 {return Ok(vec![clue.into()]);}
    let points=clue.chars().collect::<Vec<_>>();let mut parts=Vec::new();let mut start=0;
    for (i,c) in points.iter().enumerate() {
        let numeric=*c==',' && i>0 && i+1<points.len() && points[i-1].is_ascii_digit() && points[i+1].is_ascii_digit();
        if "。；;！？\n，,".contains(*c) && !numeric {parts.push(points[start..=i].iter().collect::<String>());start=i+1;}
    }
    if start<points.len(){parts.push(points[start..].iter().collect());}
    let connective=regex::Regex::new("并且|以及|同时|另外|此外").unwrap();
    let continuation=regex::Regex::new("^(核验|请核验|确认|请确认|其|该|它|并|以及|同时|另外|此外|报告涉及)").unwrap();
    let subject_end=regex::Regex::new(r"于|在[0-9]|[0-9]{4}年|正式发布|发布|宣布|发行|核验|：|:").unwrap();
    let mut result=Vec::new();let mut subject=String::new();
    for part in parts {
        // Connectives are semantic boundaries only when a clause itself exceeds the API limit.
        let clauses=if part.chars().count()>70 {connective.split(&part).collect::<Vec<_>>()} else {vec![part.as_str()]};
        for clause in clauses {
            let clause=clause.trim();if clause.is_empty(){continue;}
            let carries=continuation.is_match(clause);
            if !carries {
                subject=subject_end.find(clause).map(|m|clause[..m.start()].trim().to_owned()).filter(|v|!v.is_empty()).unwrap_or_default();
            }
            let query=if carries && !subject.is_empty(){format!("{subject}：{clause}")}else{clause.into()};
            if query.chars().count()>70 {return Err("SEARCH_QUERY_UNPLANNABLE");}
            result.push(query);
        }
    }
    if result.is_empty(){Err("SEARCH_QUERY_UNPLANNABLE")}else{Ok(result)}
}

