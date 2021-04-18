<!DOCTYPE html>

<html lang="en">
<head>
    <title>IMAP Backup</title>
    <style>
        html, body {
            margin: 0;
            padding: 0;
        }
        h1, p {
            padding: 0 1rem;
        }
        .mails {
            display: grid;
            grid-template-columns: 20% max-content auto 20% 20% 15%;
        }
        .mails > div {
            text-overflow: ellipsis;
            white-space: nowrap;
            overflow: hidden;
            padding: 0.25rem 0.5rem;
        }
        .mails > div:nth-child(12n+1),
        .mails > div:nth-child(12n+2),
        .mails > div:nth-child(12n+3),
        .mails > div:nth-child(12n+4),
        .mails > div:nth-child(12n+5),
        .mails > div:nth-child(12n+6) {
            background-color: #e6e6e6;
        }
        .mails .header {
            font-size: 1.25rem;
            font-weight: bold;
        }
    </style>
</head>
<body>
    <h1>IMAP Backup</h1>
    <#if count??><p>Mail count ${count}</p></#if>

    <#if mails??>
    <#list mails>
    <div class="mails">
        <div class="header">Folder</div>
        <div class="header">Received at</div>
        <div class="header">Subject</div>
        <div class="header">From</div>
        <div class="header">To</div>
        <div class="header">Attachments</div>
        <#items as mail>
        <div>${mail.folder}</div>
        <div>${mail.date}</div>
        <div><#if mail.link??><a href="${mail.link}">${mail.subject}</a><#else>${mail.subject}</#if></div>
        <div>${mail.from}</div>
        <div>${mail.to}</div>
        <#if mail.attachments??><div title="${mail.attachments}">${mail.attachments}</div><#else><div>&nbsp;</div></#if>
        </#items>
    </div>
    <#else>
    <p>No mails</p>
    </#list>
    </#if>
</body>
</html>