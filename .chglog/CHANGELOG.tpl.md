{{ if .Versions -}}
{{ range .Versions }}
## {{ .Tag.Name }} - {{ datetime "2006-01-02" .Tag.Date }}
{{ range .CommitGroups }}
### {{ .Title }}
{{ range .Commits }}
- {{ if .Scope }}**{{ .Scope }}:** {{ end }}{{ .Subject }}
{{ end }}
{{ end }}
{{ end -}}
{{ else -}}
## 更新说明

- 常规更新与改进
{{ end -}}
