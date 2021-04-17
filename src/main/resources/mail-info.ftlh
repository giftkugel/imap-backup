<#ftl auto_esc=false>
<#if subject??>Subject: ${subject}</#if>
<#if date??>Received at: ${date}</#if>
<#if contentType??>Content-Type: ${contentType}</#if>

<#if from??>From: ${from}</#if>
<#if to??>To: ${to}</#if>
<#if attachments??>Attachments: ${attachments}</#if>