VerZanctuary

A sidecar code sanctuary for developers and AI agents.
Never lose your work. Always know what changed. Experiment with confidence.
🧩 Why VerZanctuary?

AI agents (and humans) are powerful—sometimes dangerously so.
When you let an agent or an experiment loose on your codebase, you need a safety net that’s:

    Automatic: Snapshots your whole project in a sidecar Git repo with a single command or API call.

    Unintrusive: Leaves your main Git repo untouched.

    Transparent: Every snapshot, diff, and restore is logged and traceable—no more mystery code changes.

    Fast and cheap: Snapshots take seconds and use minimal storage.

    Human- and AI-friendly: Works as a daily developer tool, or as part of your AI workflow.

🚀 How It Works

    Initialize VerZanctuary for your project.

verzanctuary init

Create a sanctuary snapshot before an experiment, AI run, or refactor.

verzanctuary backup --scenario before-ai-consult

Let your AI or your team do its thing.

Take another snapshot after changes.

verzanctuary backup --scenario working-state

See exactly what changed.

    verzanctuary diff --from <before-snapshot> --to <after-snapshot>

    Restore, inspect, or compare at any time—without polluting your main Git repo.

All actions are logged in a machine-readable JSONL file:
Audit what happened, when, and why.
Every snapshot is a real Git branch—so you can use git log, git diff, or even check out old states in a safe, disposable workspace.
🛡️ Why Trust VerZanctuary?

    Zero impact: Never touches your main .git repo.

    Bulletproof backup: Your project can always be restored, even after catastrophic mistakes.

    AI and agent-safe: Designed for untrusted automation, generative AI, or bulk refactoring.

🦾 AI Agent Integration

VerZanctuary is built for human/AI hybrid teams and safe agent-driven code workflows:

    Agents:

        Snapshot the codebase before making changes.

        Apply changes, tests, or refactors.

        Snapshot again and submit a diff (or patch) for review.

    Humans:

        See and approve what changed.

        Restore or roll back instantly if needed.

        Trace the why and who of every experiment.

💡 Use Cases

    Try out LLM-based refactors without fear.

    Run agent experiments and always recover from breakage.

    End-of-day “sanctuaries” before you go home.

    Safe space for code mods, big migrations, or onboarding new tools.

    Track, compare, and audit changes in legacy or high-risk codebases.

🧰 Features

    Snapshot entire projects as sidecar Git branches (never touches your main Git).

    Restore, diff, or checkout any snapshot at any time.

    Structured action log (JSONL) for every operation.

    Full CLI and API for easy scripting or agent integration.

    Fast, local, and air-gapped by design.

🛠️ Quick Start

# Initialize in your project root (once)
verzanctuary init

# Before an AI experiment:
verzanctuary backup --scenario before-ai-consult

# After experiment:
verzanctuary backup --scenario working-state

# See what changed:
verzanctuary diff --latest

# Restore previous snapshot:
verzanctuary checkout <branch>

📝 FAQ

Q: Will this affect my existing .git repo?
A: No! VerZanctuary uses a sidecar repo outside your codebase.

Q: What if I accidentally mess up my code?
A: Just restore a snapshot, or diff to see exactly what changed.

Q: Can I use this for human work too?
A: Absolutely—end-of-day backups, demos, onboarding, audits, you name it.

Q: Is it fast?
A: Snapshots only take as long as copying changed files and making a commit—typically just seconds.

Q: Is it safe for AI agents?
A: Yes. Designed from the ground up for “sandboxed” experimentation and easy recovery.
❤️ Contributing & Vision

VerZanctuary is open source and ready for the future of AI-powered development.
Join us to make experimentation, agent-driven development, and everyday coding safer and more powerful for everyone.
📣 Get Started Now!

Clone and run the CLI or API in your own projects.

Integrate with your AI or automation workflows.

    Share feedback, issues, or feature requests—help us build the safety net for the next generation of software.

VerZanctuary—Because you deserve a safe place to experiment.
For humans, for agents, for the future.
(Feel free to edit, personalize, or ask for a more technical/short/long version. Happy to tweak for any target audience or add example screenshots!)