import type { AlarmSeverity, AlarmStatus } from "../../types/api";

export function severityTone(severity: AlarmSeverity) {
  if (severity === "CRITICAL") return "red" as const;
  if (severity === "WARNING") return "yellow" as const;
  return "blue" as const;
}

export function alarmStatusTone(status: AlarmStatus) {
  if (status === "OPEN") return "red" as const;
  if (status === "ACKNOWLEDGED") return "yellow" as const;
  return "green" as const;
}
