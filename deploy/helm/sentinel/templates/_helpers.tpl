{{- define "sentinel.secretName" -}}
{{- if .Values.secrets.existingSecret -}}{{ .Values.secrets.existingSecret }}{{- else -}}{{ .Release.Name }}-sentinel{{- end -}}
{{- end -}}
{{- define "sentinel.dbUrl" -}}
{{- if .Values.database.internal -}}jdbc:postgresql://{{ .Release.Name }}-postgres:5432/sentinel_audit{{- else -}}{{ required "database.url é obrigatório quando database.internal=false" .Values.database.url }}{{- end -}}
{{- end -}}
