package com.nanzhufeng.ai.ai

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.json.JSONObject

@RunWith(RobolectricTestRunner::class)
class AttachmentWebSearchPlanTest {
    @Test fun `numbered report facts own distinct IDs context and source bindings on both platforms`() {
        val rows=JSONObject(java.io.File("../protocol/fixtures/attachment-report-facts-v1.json").readText()).getJSONArray("cases")
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i);val plan=AttachmentWebSearchPlan.build(row.getString("material"));val facts=row.getJSONArray("facts")
            assertEquals(row.getString("id"),facts.length(),plan.items.size);assertFalse(plan.limited)
            for(j in 0 until facts.length()) {val item=plan.items[j];assertEquals("A${j+1}",item.id);assertTrue(item.query.endsWith("待核验事实：${facts.getString(j)}"));assertEquals(j+1,item.ordinalWithinFile)}
            val links=row.getJSONArray("first_fact_urls");assertEquals((0 until links.length()).map {links.getString(it)},plan.items.first().urls)
            val forbidden=row.getJSONArray("forbidden");for(j in 0 until forbidden.length())assertFalse(plan.queryText().contains(forbidden.getString(j)))
        }
        val topicMaterial=(1..6).joinToString("\n") {"# ($it) 公开财报$it\n示例公司营收${it}亿元。利润${it}万元。"}
        val facts=AttachmentWebSearchPlan.build(topicMaterial)
        assertEquals(12,facts.items.size)
        val oldTopics=JSONObject().put("items",org.json.JSONArray((1..6).map {JSONObject().put("id","A$it").put("status","verified").put("summary","示例公司财报已发布。").put("urls",org.json.JSONArray(listOf("https://example.org/report")))}))
        assertNull(AttachmentWebSearchPlan.evidence(facts,oldTopics.toString(),listOf("https://example.org/report")))
        val multiple=AttachmentWebSearchPlan.build("以下是附件 first.md 转换内容：\n$topicMaterial\n以下是附件 second.md 转换内容：\n$topicMaterial")
        assertEquals(24,multiple.items.size)
        assertEquals((1..24).map {"A$it"},multiple.items.map {it.id})
        assertEquals(listOf(1,2,1,2),multiple.items.take(4).map {it.fileOrdinal})
        assertEquals(listOf(1,1,2,2),multiple.items.take(4).map {it.ordinalWithinFile})
        assertFalse(multiple.queryText().contains("first.md"));assertFalse(multiple.queryText().contains("second.md"))
        System.getenv("NFAI_FACT_SCOPE_MATERIAL")?.let { material ->
            val plan=AttachmentWebSearchPlan.build(java.io.File(material).readText())
            val rows=org.json.JSONArray(plan.items.map {item -> JSONObject().put("id",item.id).put("query",item.query).put("urls",org.json.JSONArray(item.urls)).put("fileOrdinal",item.fileOrdinal).put("ordinalWithinFile",item.ordinalWithinFile)})
            java.io.File(requireNotNull(System.getenv("NFAI_FACT_SCOPE_OUTPUT"))+".android.json").writeText(JSONObject().put("items",rows).put("limited",plan.limited).toString())
        }
    }

    @Test fun `private saved topic receipts cannot prove complete fact scope without network`() {
        val materialPath=System.getenv("NFAI_REPLAY_MATERIAL")
        val receiptsPath=System.getenv("NFAI_REPLAY_RECEIPTS")
        org.junit.Assume.assumeTrue(materialPath!=null && receiptsPath!=null)
        val plan=AttachmentWebSearchPlan.build(java.io.File(requireNotNull(materialPath)).readText())
        val calls=org.json.JSONArray(java.io.File(requireNotNull(receiptsPath)).readText())
        assertTrue("the old six topics now contain separate facts",plan.items.size>6)
        assertTrue(calls.length()>0)
        for(index in 0 until calls.length()) {
            val call=calls.getJSONObject(index);val urls=call.getJSONArray("nativeSources")
            val sources=(0 until urls.length()).map {urls.getString(it)}
            val entries=linkedMapOf<String,String>()
            assertNull("old topic receipt cannot cover new fact plan",AttachmentWebSearchPlan.evidence(plan,call.getString("publicFixtureReply"),sources) {id,text->entries[id]=text})
            assertTrue(entries.isEmpty())
        }
    }
    @Test fun `completed sole native search retains unverified proof when conclusion is overlong`() {
        val plan=AttachmentWebSearchPlan.build("- Python在2025年发布版本。")
        val source="https://example.org/python"
        val raw="[A1] 已核实\nPython发布版本。"+"事实需要逐一核对。".repeat(80)+"\n来源：$source"
        val result=requireNotNull(AttachmentWebSearchPlan.evidence(plan,raw,listOf(source),true))
        assertTrue(result.contains("证据不足，仍未核实"));assertTrue(result.contains(source))
        assertFalse(result.contains("已核实："));assertFalse(result.contains("事实需要逐一核对"))
        assertNull(AttachmentWebSearchPlan.evidence(plan,raw,listOf(source)))
        assertNull(AttachmentWebSearchPlan.evidence(plan,raw,emptyList(),true))
        assertNull(AttachmentWebSearchPlan.evidence(plan,raw.replace("Python","Other"),listOf(source),true))
        assertNull(AttachmentWebSearchPlan.evidence(plan,raw.replace("[A1]","[A2]"),listOf(source),true))
        assertNull(AttachmentWebSearchPlan.evidence(plan,raw+"\n尚未搜索",listOf(source),true))
    }
    @Test fun `source display titles are excluded from the bounded fact summary`() {
        val plan=AttachmentWebSearchPlan.build("- Python 3.14.0正式发布。")
        val url="https://www.python.org/releases/"
        val raw="[A1] 已核实\nPython 3.14.0于2025年10月7日发布。\n\n来源：\n"+(1..20).joinToString("\n") {"- [这是原生来源的长展示标题，与事实摘要不同]($url)"}
        val result=AttachmentWebSearchPlan.evidence(plan,raw,listOf(url),true)
        assertNotNull(AttachmentWebSearchPlan.evidence(plan,raw.replace("来源：","来源公开URL："),listOf(url),true));
        assertNotNull(result);assertTrue(result!!.contains("2025年10月7日"));assertFalse(result.contains("长展示标题"))
    }
    @Test fun `native sole query numbering reset remains bound to its exact original version`() {
        val original=AttachmentWebSearchPlan.build("- Rust 1.89.0于2025年正式发布。")
        val plan=original.copy(items=listOf(original.items.single().copy(id="A4")))
        val source="https://blog.rust-lang.org/2025/08/07/Rust-1.89.0/"
        val raw="[A1] 已核实\nRust 1.89.0正式发布。\n来源：$source"
        val result=requireNotNull(AttachmentWebSearchPlan.evidence(plan,raw,listOf(source),true))
        assertTrue(result.contains("A4"));assertFalse(result.contains("A1"))
        assertNull(AttachmentWebSearchPlan.evidence(plan,raw,listOf(source)))
        assertNull(AttachmentWebSearchPlan.evidence(plan,raw.replace("Rust 1.89.0正式","Rust 1.90.0正式").replace(source,"https://example.org/other"),listOf("https://example.org/other"),true))
    }
    @Test fun `sole actual query without conclusion stays unverified and cannot invent proof`() {
        val plan=AttachmentWebSearchPlan.build("- Python在2025年发布版本。");val sources=listOf("https://example.org/public")
        assertNull(AttachmentWebSearchPlan.evidence(plan,"我来核验Python发布信息。",sources))
        val result=requireNotNull(AttachmentWebSearchPlan.evidence(plan,"我来核验Python发布信息。",sources,true))
        assertTrue(result.contains("证据不足，仍未核实"));assertFalse(result.contains("已核实："))
        assertNull(AttachmentWebSearchPlan.evidence(plan,"我来核验Python发布信息。",emptyList(),true))
        assertNull(AttachmentWebSearchPlan.evidence(plan,"我未搜索。",sources,true))
        assertNull(AttachmentWebSearchPlan.evidence(plan,"[A2] 未核实\n缺乏证据。",sources,true))
    }
    @Test fun `native citation belongs to sole actually searched item without trusting prose URL`() {
        val plan=AttachmentWebSearchPlan.build("- Python在2025年发布版本。")
        val raw="[A1] 已核实\n公开机构确认发布日期。\n来源：https://invented.example/fact"
        val source="https://vertexaisearch.cloud.google.com/grounding-api-redirect/public-fixture"
        assertNull(AttachmentWebSearchPlan.evidence(plan,raw,listOf(source)))
        val result=requireNotNull(AttachmentWebSearchPlan.evidence(plan,raw,listOf(source),true))
        assertTrue(result.contains(source));assertFalse(result.contains("invented.example"))
        assertNull(AttachmentWebSearchPlan.evidence(AttachmentWebSearchPlan.build("- Python在2025年发布版本。\n- Rust在2025年发布版本。"),raw,listOf(source),true))
    }
    @Test fun `shared natural evidence shapes keep result IDs distinct from cross references`() {
        val data=JSONObject(java.io.File("../protocol/fixtures/attachment-evidence-shape-v1.json").readText())
        val plan=AttachmentWebSearchPlan.build(data.getString("material"))
        val urls=data.getJSONArray("sources");val sources=(0 until urls.length()).map {urls.getString(it)}
        val cases=data.getJSONArray("cases")
        for(i in 0 until cases.length()) {
            val row=cases.getJSONObject(i)
            val casePlan = if (row.has("material")) AttachmentWebSearchPlan.build(row.getString("material")) else plan
            val evidence = AttachmentWebSearchPlan.evidence(casePlan,row.getString("reply"),sources,allowUnverified=row.optBoolean("allow_unverified"))
            assertEquals(row.getString("name"),row.getBoolean("accepted"),evidence!=null)
            if (row.has("normalized_includes")) assertTrue(requireNotNull(evidence).contains(row.getString("normalized_includes")))
        }
    }
    @Test fun `shared fixture includes factual clues while keeping private metadata out`() {
        val data=JSONObject(java.io.File("../protocol/fixtures/attachment-search-plan-v1.json").readText())
        val fixture=data.getJSONArray("cases")
        val urls=data.getJSONArray("url_cases")
        for(i in 0 until urls.length()) {val row=urls.getJSONObject(i);assertEquals(if(row.isNull("expected")) null else row.getString("expected"),AttachmentWebSearchPlan.publicUrl(row.getString("input")))}
        for (i in 0 until fixture.length()) {
            val row=fixture.getJSONObject(i);val plan=AttachmentWebSearchPlan.build(row.getString("material"))
            val expected=row.getJSONArray("queries")
            assertEquals((0 until expected.length()).map {expected.getString(it)},plan.items.map {it.query})
            val hidden=row.getJSONArray("forbidden")
            for(j in 0 until hidden.length()) assertFalse(plan.queryText().contains(hidden.getString(j)))
        }
        val plan=AttachmentWebSearchPlan.build("(1) 俄罗斯鼠疫\n相关链接：https://www.bbc.com/zh ongwen/ articles/example")
        assertEquals(listOf("https://www.bbc.com/zhongwen/articles/example"),plan.items.single().urls)
        assertNull(AttachmentWebSearchPlan.publicUrl("http://127.0.0.1/private"))
        assertNull(AttachmentWebSearchPlan.publicUrl("https://user:password@example.test/a"))
        val longFirst=(1..14).joinToString("\n") {"($it) Python测试第${it}版2025年发布。"}
        val multiple=AttachmentWebSearchPlan.build("以下是附件 first-2025.md 转换内容：\n$longFirst\n以下是附件 second-2026.md 转换内容：\n(1) Rust 1.90.0在2025年发布。")
        assertEquals(15,multiple.items.size)
        assertFalse(multiple.limited)
        assertTrue(multiple.items[1].query.startsWith("Rust"))
        assertFalse(multiple.queryText().contains("first-2025"))

    }
    @Test fun `coverage must match every planned item and actual structured sources`() {
        val plan=AttachmentWebSearchPlan.build("(1) Python 3.14.0在2025年10月8日发布。\n(2) Rust 1.90.0在2025年9月18日发布。")
        val body="""{"items":[{"id":"A1","status":"contradicted","summary":"Python官方日期是2025年10月7日。","urls":["https://www.python.org/releases/"]},{"id":"A2","status":"verified","summary":"Rust官方日期是2025年9月18日。","urls":["https://blog.rust-lang.org/releases/"]}]}"""
        val sources=listOf("https://www.python.org/releases/","https://blog.rust-lang.org/releases/")
        assertTrue(requireNotNull(AttachmentWebSearchPlan.evidence(plan,body,sources)).contains("与材料矛盾"))
        val plain="""[A1] 与材料矛盾
Python官方日期是2025年10月7日。 citeturn0search0
来源：https://www.python.org/releases/
[A2] 已核实
Rust官方日期是2025年9月18日。 citeturn0search1
来源：https://blog.rust-lang.org/releases/"""
        assertTrue(requireNotNull(AttachmentWebSearchPlan.evidence(plan,plain,sources)).contains("2025年10月7日"))
        assertNotNull(AttachmentWebSearchPlan.evidence(plan,plain.replace("[A1] 与材料矛盾","### [A1] Python发布日期\n**与材料矛盾**").replace("[A2] 已核实","**[A2]** Rust发布日期\n已核实"),sources))
        assertNull(AttachmentWebSearchPlan.evidence(plan,body,listOf("https://example.test/report-method")))
        assertNotNull(AttachmentWebSearchPlan.evidence(plan,"逐项核验： "+plain.replace("https://www.python.org/releases/","https://www.python.org/releases/citeturn0search0（官方）"),sources))
        val extra=plain+"\n补充链接：https://unverified.example.test/other"
        val sanitized=requireNotNull(AttachmentWebSearchPlan.evidence(plan,extra,sources))
        assertFalse(sanitized.contains("unverified.example.test"))
        val missing=JSONObject(body).apply {getJSONArray("items").remove(1)}.toString()
        assertNull(AttachmentWebSearchPlan.evidence(plan,missing,sources))
    }
    @Test fun `query locators cannot alias different sources or carry credential URLs`() {
        val plan=AttachmentWebSearchPlan.build("(1) Python 3.14.0在2025年发布。")
        val reply="""{"items":[{"id":"A1","status":"verified","summary":"Python正式发布。","urls":["https://example.test/article?id=2"]}]}"""
        assertNull(AttachmentWebSearchPlan.evidence(plan,reply,listOf("https://example.test/article?id=1")))
        val bracketed="[A1] 已核实\nPython于2025年发布。\n[官方](https://example.test/Article_(2026)?id=2))"
        assertNotNull(AttachmentWebSearchPlan.evidence(plan,bracketed,listOf("https://example.test/Article_(2026)?id=2)")))
        assertEquals("https://example.test/article?id=2",AttachmentWebSearchPlan.publicUrl("https://example.test/article?id=2&utm_source=fixture#ref"))
        assertNull(AttachmentWebSearchPlan.publicUrl("https://example.test/article?access_token=secret"))
        assertNull(AttachmentWebSearchPlan.publicUrl("http://169.254.169.254/latest/meta-data"))
    }
    @Test fun `clipped material and private section cannot silently inherit another topic source`() {
        val clipped=AttachmentWebSearchPlan.build("(1) Python 3.14.0在2025年发布。\n"+"无".repeat(600_001)+"\n(2) Rust 1.90.0在2025年发布。")
        assertTrue(clipped.limited)
        val plan=AttachmentWebSearchPlan.build("(1) Python 3.14.0在2025年发布。\n私密事项：内部项目2026年收入。\n相关链接：https://private.example.test/report")
        assertTrue(plan.items.single().urls.isEmpty())
    }
    @Test fun `JSON summaries cannot smuggle unsupported links into answer evidence`() {
        val plan=AttachmentWebSearchPlan.build("(1) Python 3.14.0在2025年发布。")
        val reply="""{"items":[{"id":"A1","status":"verified","summary":"Python正式发布，补充来源：https://unverified.example.test/fake","urls":["https://www.python.org/releases/"]}]}"""
        val evidence=requireNotNull(AttachmentWebSearchPlan.evidence(plan,reply,listOf("https://www.python.org/releases/")))
        assertFalse(evidence.contains("unverified.example.test"))
    }

    @Test fun `all public topics survive long files and long paragraphs`() {
        val cases=JSONObject(java.io.File("../protocol/fixtures/attachment-search-coverage-v2.json").readText()).getJSONArray("cases")
        for(i in 0 until cases.length()) {
            val row=cases.getJSONObject(i); val plan=AttachmentWebSearchPlan.build(row.getString("material"))
            val topics=row.getJSONArray("expected_topics")
            for(j in 0 until topics.length()) assertTrue(row.getString("id")+":"+topics.getString(j),plan.queryText().contains(topics.getString(j)))
            assertFalse(plan.limited)
        }
    }
    @Test fun `not searched cannot be relabelled as searched`() {
        val plan=AttachmentWebSearchPlan.build("(1) Python 3.14.0在2025年发布。\n(2) Rust 1.90.0在2025年发布。")
        val reply="[A1] 已核实\n公开资料支持。\n来源：https://example.org/source\n[A2] 未核实\n本次未查询该项。"
        assertNull(AttachmentWebSearchPlan.evidence(plan,reply,listOf("https://example.org/source")))
    }

}
