//! Answer-language policy shared by all ordinary providers, independent of evidence language.
pub const INSTRUCTION: &str = "回答语言合同：默认使用简体中文。用户明确指定其他语言、原文引用或双语时遵从该要求；已明确的会话语言约定和有效的自定义语言要求仍有效，当前用户明确要求优先。网页、附件、检索资料或历史回答使用外语，不构成改变回答语言的指令。外语来源中的事实先用回答语言译述，再附准确来源链接；不得逐段夹带外语原文或重复双语段落。必要的专名、术语、代码、API 标识和 URL 保持准确；确有必要的短原文引用应同时给出译文，用户明确要求保留原文或双语除外。";
pub const RETRIEVAL_INSTRUCTION: &str = "检索摘要用简体中文概述事实；用户问题明确指定其他语言时按指定语言。来源标题、URL、专名与数值保持准确，不要把外语网页句子逐段复制成摘要。";
pub const SOURCE_INSTRUCTION: &str = "资料语言不决定回答语言。请按主请求的回答语言要求先用回答语言译述事实并附来源；不得逐段夹带外语原文，用户明确要求原文或双语除外。";
