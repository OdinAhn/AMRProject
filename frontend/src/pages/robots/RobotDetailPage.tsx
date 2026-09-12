import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState, type FormEvent } from "react";
import { Link, useParams } from "react-router";
import {
  Badge,
  Button,
  Card,
  ErrorBanner,
  Field,
  Input,
  PageHeader,
  Select,
  Table,
  Td,
  Th,
} from "../../components/ui";
import { ApiError, api } from "../../lib/apiClient";
import type {
  CreateRobotConfigurationRequest,
  RobotConfigurationResponse,
  RobotLifecycleStatus,
  RobotResponse,
  RobotStateResponse,
} from "../../types/api";

const LIFECYCLE_STATUSES: RobotLifecycleStatus[] = ["REGISTERED", "IN_SERVICE", "MAINTENANCE", "DECOMMISSIONED"];

export function RobotDetailPage() {
  const { robotId } = useParams<{ robotId: string }>();
  const queryClient = useQueryClient();
  const [configError, setConfigError] = useState<string | null>(null);
  const [form, setForm] = useState({
    maxPayloadKg: "50",
    maxSpeedMps: "1.5",
    batteryCapacityWh: "500",
    chargingLowThresholdPct: "20",
    chargingTargetPct: "90",
  });

  const robotQuery = useQuery({
    queryKey: ["robots", robotId],
    queryFn: () => api.get<RobotResponse>(`/api/admin/robots/${robotId}`),
  });

  const stateQuery = useQuery({
    queryKey: ["robots", robotId, "state"],
    queryFn: () => api.get<RobotStateResponse>(`/api/admin/robots/${robotId}/state`),
    retry: false,
  });

  const configsQuery = useQuery({
    queryKey: ["robots", robotId, "configurations"],
    queryFn: () => api.get<RobotConfigurationResponse[]>(`/api/admin/robots/${robotId}/configurations`),
  });

  const updateLifecycle = useMutation({
    mutationFn: (lifecycleStatus: RobotLifecycleStatus) =>
      api.patch<RobotResponse>(`/api/admin/robots/${robotId}/lifecycle-status`, { lifecycleStatus }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["robots", robotId] }),
  });

  const createConfig = useMutation({
    mutationFn: (body: CreateRobotConfigurationRequest) =>
      api.post<RobotConfigurationResponse>(`/api/admin/robots/${robotId}/configurations`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["robots", robotId, "configurations"] });
      queryClient.invalidateQueries({ queryKey: ["robots", robotId] });
      setConfigError(null);
    },
    onError: (err) => setConfigError(err instanceof ApiError ? err.message : "설정 생성에 실패했습니다."),
  });

  const onSubmitConfig = (e: FormEvent) => {
    e.preventDefault();
    createConfig.mutate({
      maxPayloadKg: Number(form.maxPayloadKg),
      maxSpeedMps: Number(form.maxSpeedMps),
      batteryCapacityWh: Number(form.batteryCapacityWh),
      chargingLowThresholdPct: Number(form.chargingLowThresholdPct),
      chargingTargetPct: Number(form.chargingTargetPct),
    });
  };

  const robot = robotQuery.data;

  return (
    <div>
      <PageHeader
        title={robot ? `${robot.name} (${robot.code})` : "로봇 상세"}
        actions={
          <Link to="/robots" className="text-sm text-indigo-600 hover:underline">
            ← 로봇 목록
          </Link>
        }
      />

      {robot && (
        <Card className="mb-4">
          <h2 className="mb-3 text-sm font-semibold text-gray-700">운영 상태</h2>
          <div className="flex items-center gap-3">
            <Badge tone={robot.lifecycleStatus === "IN_SERVICE" ? "green" : "gray"}>{robot.lifecycleStatus}</Badge>
            <Select
              className="w-48"
              value=""
              onChange={(e) => e.target.value && updateLifecycle.mutate(e.target.value as RobotLifecycleStatus)}
            >
              <option value="">상태 변경...</option>
              {LIFECYCLE_STATUSES.filter((s) => s !== robot.lifecycleStatus).map((status) => (
                <option key={status} value={status}>
                  {status}
                </option>
              ))}
            </Select>
          </div>
        </Card>
      )}

      <Card className="mb-4">
        <h2 className="mb-3 text-sm font-semibold text-gray-700">현재 로봇 상태</h2>
        {stateQuery.data ? (
          <div className="grid grid-cols-3 gap-3 text-sm">
            <div>
              연결: <Badge tone={stateQuery.data.connectionState === "ONLINE" ? "green" : "red"}>{stateQuery.data.connectionState}</Badge>
            </div>
            <div>운전 모드: {stateQuery.data.operationMode}</div>
            <div>동작: {stateQuery.data.motionState}</div>
            <div>안전: {stateQuery.data.safetyState}</div>
            <div>적재: {stateQuery.data.loadState}</div>
            <div>위치 추정: {stateQuery.data.localizationState}</div>
            <div>배터리: {stateQuery.data.batteryPct}%</div>
          </div>
        ) : (
          <p className="text-sm text-gray-500">아직 보고된 상태가 없습니다 (명령을 받은 적 없는 로봇).</p>
        )}
      </Card>

      <Card className="mb-4">
        <h2 className="mb-3 text-sm font-semibold text-gray-700">새 설정 생성</h2>
        <form onSubmit={onSubmitConfig} className="grid grid-cols-5 gap-3">
          <Field label="최대 적재(kg)">
            <Input
              type="number"
              step="0.1"
              value={form.maxPayloadKg}
              onChange={(e) => setForm({ ...form, maxPayloadKg: e.target.value })}
              required
            />
          </Field>
          <Field label="최대 속도(m/s)">
            <Input
              type="number"
              step="0.1"
              value={form.maxSpeedMps}
              onChange={(e) => setForm({ ...form, maxSpeedMps: e.target.value })}
              required
            />
          </Field>
          <Field label="배터리 용량(Wh)">
            <Input
              type="number"
              step="0.1"
              value={form.batteryCapacityWh}
              onChange={(e) => setForm({ ...form, batteryCapacityWh: e.target.value })}
              required
            />
          </Field>
          <Field label="저배터리 임계값(%)">
            <Input
              type="number"
              value={form.chargingLowThresholdPct}
              onChange={(e) => setForm({ ...form, chargingLowThresholdPct: e.target.value })}
              required
            />
          </Field>
          <Field label="충전 목표(%)">
            <Input
              type="number"
              value={form.chargingTargetPct}
              onChange={(e) => setForm({ ...form, chargingTargetPct: e.target.value })}
              required
            />
          </Field>
          <div className="col-span-5">
            <Button type="submit" disabled={createConfig.isPending}>
              설정 생성 및 적용
            </Button>
          </div>
        </form>
        <ErrorBanner message={configError} />
      </Card>

      <Card>
        <h2 className="mb-3 text-sm font-semibold text-gray-700">설정 이력</h2>
        <Table>
          <thead>
            <tr>
              <Th>버전</Th>
              <Th>최대 적재</Th>
              <Th>최대 속도</Th>
              <Th>배터리 용량</Th>
              <Th>저배터리 임계값</Th>
              <Th>충전 목표</Th>
            </tr>
          </thead>
          <tbody>
            {(configsQuery.data ?? []).map((config) => (
              <tr key={config.id}>
                <Td>v{config.versionNo}</Td>
                <Td>{config.maxPayloadKg} kg</Td>
                <Td>{config.maxSpeedMps} m/s</Td>
                <Td>{config.batteryCapacityWh} Wh</Td>
                <Td>{config.chargingLowThresholdPct}%</Td>
                <Td>{config.chargingTargetPct}%</Td>
              </tr>
            ))}
          </tbody>
        </Table>
      </Card>
    </div>
  );
}
