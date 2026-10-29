import json, sqlite3, sys
from pathlib import Path
root=Path(sys.argv[1]).resolve()
if not (root/'.nanfeng-windows-acceptance.json').is_file():
    raise SystemExit('Only marked synthetic root is allowed')
marker=json.loads((root/'.nanfeng-windows-acceptance.json').read_text(encoding='utf-8-sig'))
if marker.get('identifier')!='com.nanzhufeng.ai.desktop.windowsacceptance' or not root.name.startswith('nanfeng-ai-windows-acceptance.'):
    raise SystemExit('Invalid acceptance identity')
area=sys.argv[2] if len(sys.argv)>2 else 'WORK'
if area not in ('CHAT','WORK'):
    raise SystemExit('Invalid data area')
database=root/('work-area/workspace.sqlite3' if area=='WORK' else 'workspace.sqlite3')
connection=sqlite3.connect(database.as_uri()+'?mode=ro',uri=True)
rows=connection.execute('SELECT run_id,status,notification_state,notification_safe_code,plan_id,attempt_id,requested_model_id,actual_model_id,input_tokens,output_tokens,charge_micros,safe_error_code FROM desktop_reminder_runs_v1').fetchall()
drafts=connection.execute('SELECT draft_id,status,attempt_id,requested_model_id,actual_model_id,input_tokens,output_tokens,cached_input_tokens,charge_micros,safe_error_code,retry_count,updated_at_ms FROM desktop_reminder_drafts_v1').fetchall()
connection.close()
ledger=database.parent/'usage-ledger/usage-ledger-v1.sqlite3'
usage=[]
if ledger.is_file():
    connection=sqlite3.connect(ledger.as_uri()+'?mode=ro',uri=True)
    usage=connection.execute('SELECT execution_id,attempt_id,requested_model_id,actual_model_id,input_tokens,output_tokens,charge_micros,kind,fact_grade,cost_source FROM usage_ledger_entries').fetchall()
    connection.close()
print(json.dumps({'drafts':[dict(zip(['draftId','status','attemptId','requestedModelId','actualModelId','inputTokens','outputTokens','cachedInputTokens','chargeMicros','safeErrorCode','retryCount','updatedAtMs'],row)) for row in drafts], 'runs':[dict(zip(['runId','runState','notificationState','safeCode','planId','attemptId','requestedModelId','actualModelId','inputTokens','outputTokens','chargeMicros','errorCode'],row)) for row in rows], 'usage':[dict(zip(['executionId','attemptId','requestedModelId','actualModelId','inputTokens','outputTokens','chargeMicros','kind','factGrade','costSource'],row)) for row in usage]}))
