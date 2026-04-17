{{- define "mdwiki-api.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "mdwiki-api.fullname" -}}
{{- if .Values.fullnameOverride -}}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name (include "mdwiki-api.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{- define "mdwiki-api.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" -}}
{{- end -}}

{{- define "mdwiki-api.labels" -}}
helm.sh/chart: {{ include "mdwiki-api.chart" . }}
app.kubernetes.io/name: {{ include "mdwiki-api.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{- define "mdwiki-api.selectorLabels" -}}
app.kubernetes.io/name: {{ include "mdwiki-api.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
