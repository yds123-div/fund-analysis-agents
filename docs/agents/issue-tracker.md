# 议题追踪：GitHub

本仓库的议题与 PRD 以 GitHub issue 的形式管理。所有操作均使用 `gh` CLI。

## 约定

- **创建议题**：`gh issue create --title "..." --body "..."`。多行内容请使用 heredoc。
- **查看议题**：`gh issue view <number> --comments`，用 `jq` 过滤评论，同时获取标签。
- **列出议题**：`gh issue list --state open --json number,title,body,labels,comments --jq '[.[] | {number, title, body, labels: [.labels[].name], comments: [.comments[].body]}]'`，按需加上 `--label` 与 `--state` 过滤。
- **评论议题**：`gh issue comment <number> --body "..."`
- **添加/移除标签**：`gh issue edit <number> --add-label "..."` / `--remove-label "..."`
- **关闭**：`gh issue close <number> --comment "..."`

仓库可通过 `git remote -v` 推断——在克隆中运行时 `gh` 会自动识别。

## Pull request 作为 triage 入口

**PR 作为请求入口：否。** _（若本仓库将外部 PR 视为功能请求，请设为 `yes`；`/triage` 会读取此标志。）_

设为 `yes` 时，PR 与 issue 使用相同的标签与状态流程，对应使用 `gh pr` 等价命令：

- **查看 PR**：`gh pr view <number> --comments`，以及 `gh pr diff <number>` 查看差异。
- **列出待 triage 的外部 PR**：`gh pr list --state open --json number,title,body,labels,author,authorAssociation,comments`，仅保留 `authorAssociation` 为 `CONTRIBUTOR`、`FIRST_TIME_CONTRIBUTOR` 或 `NONE` 的项（排除 `OWNER`/`MEMBER`/`COLLABORATOR`）。
- **评论/打标签/关闭**：`gh pr comment`、`gh pr edit --add-label`/`--remove-label`、`gh pr close`。

GitHub 中 issue 与 PR 共享同一编号空间，因此裸 `#42` 可能是任一类型——先用 `gh pr view 42` 解析，再回退到 `gh issue view 42`。

## 当某个 skill 说「发布到议题追踪器」时

创建一个 GitHub issue。

## 当某个 skill 说「获取相关工单」时

运行 `gh issue view <number> --comments`。

## 寻路（Wayfinding）操作

供 `/wayfinder` 使用。**map** 是一个 issue，其下挂载若干 **child** issue 作为工单。

- **Map**：一个带 `wayfinder:map` 标签的 issue，正文承载 Notes / Decisions-so-far / Fog。`gh issue create --label wayfinder:map`。
- **Child 工单**：作为 GitHub 子 issue 关联到 map（对 sub-issues 端点调用 `gh api`）。若未启用 sub-issues，则把 child 加入 map 正文的 task list，并在 child 正文顶部写上 `Part of #<map>`。标签为 `wayfinder:<type>`（`research`/`prototype`/`grilling`/`task`）。一旦认领，工单分配给驱动的开发者。
- **阻塞关系**：GitHub 的**原生 issue 依赖**——规范的、UI 可见的表示。添加边：`gh api --method POST repos/<owner>/<repo>/issues/<child>/dependencies/blocked_by -F issue_id=<blocker-db-id>`，其中 `<blocker-db-id>` 是阻塞方的数值型 **database id**（`gh api repos/<owner>/<repo>/issues/<n> --jq .id`，_不是_ `#number` 或 `node_id`）。GitHub 通过 `issue_dependencies_summary.blocked_by` 上报（仅含未关闭的阻塞方——即实时门禁）。若依赖不可用，则回退到在 child 正文顶部写一行 `Blocked by: #<n>, #<n>`。当所有阻塞方都关闭时，工单即解除阻塞。
- **Frontier 查询**：列出 map 下未关闭的 child（`gh issue list --state open`，限定到 map 的 sub-issues / task list），剔除任何存在未关闭阻塞方（`issue_dependencies_summary.blocked_by > 0`，或 `Blocked by` 行中存在未关闭 issue）或已有 assignee 的项；按 map 顺序取第一个。
- **认领**：`gh issue edit <n> --add-assignee @me`——本会话的首次写入。
- **解决**：`gh issue comment <n> --body "<answer>"`，再 `gh issue close <n>`，最后把上下文指针（gist + 链接）追加到 map 的 Decisions-so-far。
