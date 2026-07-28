# CLAUDE.md

本仓库的 Agent 配置。

## Agent skills

### Issue tracker

议题与 PRD 以 GitHub issue 的形式管理（使用 `origin` 远程仓库 `yds123-div/fund-analysis-agents`），通过 `gh` CLI 操作。详见 `docs/agents/issue-tracker.md`。

### Triage labels

使用默认的五个标准 triage 标签：`needs-triage`、`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。详见 `docs/agents/triage-labels.md`。

### Domain docs

单上下文布局：仓库根目录下一份 `CONTEXT.md` + `docs/adr/`。详见 `docs/agents/domain.md`。
