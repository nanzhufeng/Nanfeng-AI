package com.nanzhufeng.ai.ai

import com.nanzhufeng.ai.domain.IndependentSearchServiceGrant
import org.json.JSONObject

internal class IndependentWebSearchClient(private val transport: ProviderChatTransport) {
    data class Source(val url:String,val title:String,val content:String,val publishDate:String?)
    sealed interface Result {
        data class Completed(val sources:List<Source>,val elapsedMillis:Long,val callId:String):Result
        data class Failed(val code:String,val unknown:Boolean=false):Result
    }
    fun search(query:String,callId:String,grant:IndependentSearchServiceGrant,fingerprint:String,
        readCredential:()->CharArray,cancellation:ProviderChatCancellation?,onAccepted:()->Unit={}):Result {
        if(!grant.allows(fingerprint)) return Result.Failed("EGRESS_AUTHORIZATION_REQUIRED")
        if(cancellation?.isCancelled()==true) return Result.Failed("USER_CANCELLED")
        if(query.isBlank() || query.codePointCount(0,query.length)>70) return Result.Failed("SEARCH_QUERY_LIMIT")
        val body=JSONObject().put("search_query",query).put("search_engine",IndependentSearchServiceGrant.ENGINE_ID)
            .put("search_intent",false).put("count",10).put("search_recency_filter","noLimit").put("content_size","high").put("request_id",callId)
        val credential=runCatching(readCredential).getOrNull() ?: return Result.Failed("CREDENTIAL_MISSING")
        val start=System.nanoTime()
        val response=try {transport.execute(ProviderChatRequest(IndependentSearchServiceGrant.ENDPOINT,body.toString(),
            readTimeoutMillis=35_000,maxResponseBytes=1_048_576,cancellation=cancellation,onAccepted=onAccepted),credential)
        } catch(cancelled:java.util.concurrent.CancellationException) {throw cancelled} catch(_:Exception) {ProviderChatOutcome.NetworkFailure(ProviderNetworkFailureKind.IO)}
        finally {credential.fill('\u0000')}
        if(cancellation?.isCancelled()==true) return Result.Failed("USER_CANCELLED")
        return when(response) {
            is ProviderChatOutcome.HttpResponse -> {
                if(response.statusCode!=200) Result.Failed("HTTP_${response.statusCode}",response.statusCode in setOf(408,504))
                else runCatching {
                    val decoded=JSONObject(response.responseBody)
                    if(decoded.has("error") && !decoded.isNull("error")) return@runCatching Result.Failed("SEARCH_SERVICE_REJECTED")
                    val results=decoded.getJSONArray("search_result")
                    val sources=(0 until results.length()).mapNotNull {index ->
                        val row=results.getJSONObject(index)
                        val url=AttachmentWebSearchPlan.publicUrl(row.optString("link")) ?: return@mapNotNull null
                        val content=row.optString("content");if(content.isBlank()) return@mapNotNull null
                        Source(url,row.optString("title").takeCodePoints(200),content.takeCodePoints(6000),row.optString("publish_date").takeIf(String::isNotBlank))
                    }
                    Result.Completed(sources,(System.nanoTime()-start)/1_000_000,callId)
                }.getOrElse {Result.Failed("RESPONSE_FORMAT")}
            }
            ProviderChatOutcome.Cancelled -> Result.Failed("USER_CANCELLED")
            ProviderChatOutcome.TimedOut -> Result.Failed("TIMEOUT",true)
            is ProviderChatOutcome.NetworkFailure -> Result.Failed(response.failureCode,true)
            else -> Result.Failed("RESPONSE_FORMAT")
        }
    }
    companion object {
        fun queries(clue:String):List<String>? {
            if(clue.isBlank()) return null
            if(clue.codePointCount(0,clue.length)<=70) return listOf(clue)
            val points=clue.codePoints().toArray();val parts=mutableListOf<String>();var start=0
            for(i in points.indices) {
                val numeric=points[i]==','.code && i>0 && i+1<points.size && points[i-1] in 48..57 && points[i+1] in 48..57
                if(points[i] in "。；;！？\n，,".codePoints().toArray() && !numeric) {parts+=String(points,start,i-start+1);start=i+1}
            }
            if(start<points.size) parts+=String(points,start,points.size-start)
            val connective=Regex("并且|以及|同时|另外|此外")
            val continuation=Regex("^(核验|请核验|确认|请确认|其|该|它|并|以及|同时|另外|此外|报告涉及)")
            val subjectEnd=Regex("于|在[0-9]|[0-9]{4}年|正式发布|发布|宣布|发行|核验|：|:")
            val result=mutableListOf<String>();var subject=""
            for(part in parts) {
                val clauses=if(part.codePointCount(0,part.length)>70) part.split(connective) else listOf(part)
                for(raw in clauses) {
                    val clause=raw.trim();if(clause.isBlank()) continue
                    val carries=continuation.containsMatchIn(clause)
                    if(!carries) subject=subjectEnd.find(clause)?.let {clause.substring(0,it.range.first).trim()}.orEmpty()
                    val query=if(carries && subject.isNotEmpty()) "$subject：$clause" else clause
                    if(query.codePointCount(0,query.length)>70) return null
                    result+=query
                }
            }
            return result.takeIf {it.isNotEmpty()}
        }
        private fun String.takeCodePoints(limit:Int):String=substring(0,offsetByCodePoints(0,minOf(limit,codePointCount(0,length))))
    }
}
