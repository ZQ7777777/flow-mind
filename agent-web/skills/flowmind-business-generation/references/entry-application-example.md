# Entry application example

## Business overview

- Business code: `entry-application`
- Business name: 入金申请
- Entry display name: 入金申请
- Page title: 发起入金申请

## Form fields

| fieldCode | name | control | required |
| --- | --- | --- | --- |
| `applicantName` | 申请人姓名 | input | yes |
| `amount` | 入金金额 | number | yes |
| `accountNumber` | 入金账号 | input | yes |

The generated `BusinessForm.vue` renders these fields and exposes validation. The generated `Apply.vue` is a standalone direct-access entry page for 入金申请 and embeds the shared start shell with the generated form. The shared start shell loads the process context, renders the attachment section, and sends the common start-submit request. No generated file implements an entry-application submit API.

The generated route should be directly accessible at `/generated/entry-application/apply` and include `meta.public: true` so it renders outside the `business-base` application shell.
