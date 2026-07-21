# Real Execution Safety

Real Execution is not part of the normal Cross DB SELECT workflow and may
change data.

Requirements:

- approved environment;
- explicit include whitelist;
- statement-type restrictions;
- review before running;
- backup/recovery plan;
- typed BAT confirmation.

Do not weaken existing safety checks.
