# 领域文档（Domain Docs）

工程类 skill 在探索代码库时应如何消费本仓库的领域文档。

## 探索前请先阅读

- 仓库根目录的 **`CONTEXT.md`**，或
- 若存在仓库根目录的 **`CONTEXT-MAP.md`**——它指向每个上下文各一份 `CONTEXT.md`。阅读与主题相关的每一份。
- **`docs/adr/`**——阅读与你即将工作的区域相关的 ADR。在多上下文仓库中，还要查看 `src/<context>/docs/adr/` 中上下文作用域的决策。

如果这些文件中有不存在的，**静默继续**。不要标记其缺失；不要建议预先创建。`/domain-modeling` skill（经 `/grill-with-docs` 与 `/improve-codebase-architecture` 到达）会在术语或决策真正被确定时按需创建它们。

## 文件结构

单上下文仓库（大多数仓库）：

```
/
├── CONTEXT.md
├── docs/adr/
│   ├── 0001-event-sourced-orders.md
│   └── 0002-postgres-for-write-model.md
└── src/
```

多上下文仓库（根目录存在 `CONTEXT-MAP.md`）：

```
/
├── CONTEXT-MAP.md
├── docs/adr/                          ← 系统级决策
└── src/
    ├── ordering/
    │   ├── CONTEXT.md
    │   └── docs/adr/                  ← 上下文特定决策
    └── billing/
        ├── CONTEXT.md
        └── docs/adr/
```

## 使用术语表的词汇

当你的输出命名某个领域概念时（在 issue 标题、重构提案、假设、测试名中），请使用 `CONTEXT.md` 中定义的术语。不要漂移到术语表明确规避的同义词。

如果你需要的概念还不在术语表中，这是一个信号——要么你在发明项目并未使用的语言（重新考虑），要么存在真实缺口（记录下来供 `/domain-modeling`）。

## 标记 ADR 冲突

如果你的输出与某个既有 ADR 相冲突，请显式指出，而不是静默覆盖：

> _与 ADR-0007（event-sourced orders）冲突——但值得重开，因为…_
