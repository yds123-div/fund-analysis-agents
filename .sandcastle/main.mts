import { run, claudeCode } from "@ai-hero/sandcastle";
import { docker } from "@ai-hero/sandcastle/sandboxes/docker";

// Simple loop: an agent that picks open issues one by one and closes them.
// Run this with: npx tsx .sandcastle/main.mts
// Or add to package.json scripts: "sandcastle": "npx tsx .sandcastle/main.mts"

await run({
  // A name for this run, shown as a prefix in log output.
  name: "worker",

  // Sandbox provider — runs the agent inside an isolated container.
  sandbox: docker({
    // Mount the host Maven cache so dependencies aren't re-downloaded on
    // every run. UID/GID alignment lets the `agent` user write to it.
    mounts: [
      { hostPath: "~/.m2", sandboxPath: "/home/agent/.m2" },
    ],
    // If the agent's tests need the live MySQL/Redis, they publish ports on
    // the host - reach them from inside the sandbox via host.docker.internal
    // (e.g. jdbc:mysql://host.docker.internal:3306/...). To put the sandbox
    // on a shared Docker network instead, uncomment and name it:
    // network: "fund-analysis-agents_default",
  }),

  // The agent provider. Pass a model string to claudeCode() — sonnet balances
  // capability and speed for most tasks. Switch to claude-opus-4-8 for harder
  // problems, or claude-haiku-4-5-20251001 for speed.
  agent: claudeCode("glm-latest"),

  // Path to the prompt file. Shell expressions inside are evaluated inside the
  // sandbox at the start of each iteration, so the agent always sees fresh data.
  promptFile: "./.sandcastle/prompt.md",

  // Maximum number of iterations (agent invocations) to run in a session.
  // Each iteration works on a single issue. Increase this to process more issues
  // per run, or set it to 1 for a single-shot mode.
  maxIterations: 3,

  // Branch strategy — head mode bind-mounts this worktree directly into the
  // sandbox; the agent commits onto the current branch (worktree-sandcastle-
  // setup). Required on Windows: merge-to-head + copyToWorktree takes the
  // non-head path, where sandcastle 0.12.0's Windows .git-mount patch
  // (patchGitMountsForWindows, ADR-0006) fails to match — it compares the
  // mount's hostRepoDir/.git against worktreeInfo.path/.git — so the .git
  // mount is left as `-v D:/x/.git:D:/x/.git:z`, whose drive-letter colon
  // Docker Desktop rejects as "too many colons". Head mode makes hostRepoDir
  // == worktreeHostPath so the patch matches and remaps the .git mount's
  // destination to a Linux path. node_modules is already visible via the
  // direct bind-mount, so copyToWorktree is dropped (onSandboxReady still
  // runs `npm install` as a safety net).
  branchStrategy: { type: "head" },

  // Lifecycle hooks — commands grouped by where they run (host or sandbox).
  hooks: {
    sandbox: {
      // onSandboxReady runs once after the sandbox is initialised and the repo is
      // synced in, before the agent starts. Use it to install dependencies or run
      // any other setup steps your project needs.
      // sandcastle sets safe.directory for the repo dir before these hooks run,
      // so git works despite the bind-mount's host ownership. gh repo set-default
      // was tried here but it calls the GitHub GraphQL API to resolve the repo's
      // node ID, and that call hit transient `Post .../graphql: EOF` resets from
      // this network - and onSandboxReady failures abort the whole run. Instead,
      // prompt.md passes `--repo yds123-div/fund-analysis-agents` to every gh
      // command, which needs no resolution call.
      onSandboxReady: [{ command: "npm install" }],
    },
  },
});
