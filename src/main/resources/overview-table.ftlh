<!DOCTYPE html>

<html lang="en">
<head>
    <title>IMAP Backup (${count} mails)</title>
    <style>
        html, body {
            margin: 0;
            padding: 0;
        }
        h1, p {
            padding: 0 1rem;
        }
        .mails > div {
            text-overflow: ellipsis;
            white-space: nowrap;
            overflow: hidden;
            padding: 0.25rem 0.5rem;
        }
        .mails .header {
            font-size: 1.25rem;
            font-weight: bold;
        }

        .mails tbody tr:nth-child(odd) {
            background-color: #e6e6e6;
        }
    </style>
</head>
<body>
    <h1>IMAP Backup</h1>
    <#if count??><p>Mail count: ${count}</p></#if>

    <#if mails??>
    <#list mails>
    <div class="mails">
        <table>
            <tbody>
                <tr>
                    <th class="header">Number</th>
                    <th class="header">Folder</th>
                    <th class="header">Received at</th>
                    <th class="header">Subject</th>
                    <th class="header">From</th>
                    <th class="header">To</th>
                    <th class="header">Attachments</th>
                </tr>
                <#items as mail>
                    <tr>
                        <td>${mail.number}</td>
                        <td>${mail.folder}</td>
                        <td>${mail.date}</td>
                        <td><#if mail.link??><a href="${mail.link}">${mail.subject}</a><#else>${mail.subject}</#if></td>
                        <td>${mail.from}</td>
                        <td>${mail.to}</td>
                        <td><#if mail.attachments??>${mail.attachments}<#else>&nbsp;</#if></td>
                    </tr>
                </#items>
            </tbody>
        </table>
    </div>
    <#else>
    <p>No mails</p>
    </#list>
    </#if>
</body>
</html>