import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router";
import { Badge, Card, ErrorBanner, Input, PageHeader, Pagination, Select, Spinner, Table, Td, Th } from "../../components/ui";
import { api, errorMessage } from "../../lib/apiClient";
import { AUDIT_TARGET_TYPES, auditActionLabel, auditTargetLabel, auditTargetPath } from "../../lib/auditActions";
import type { AuditLogResponse, PageResponse } from "../../types/api";

const PAGE_SIZE = 50;

function statusTone(status: number | null) {
  if (status === null) return "gray" as const;
  if (status < 300) return "green" as const;
  if (status === 429 || status === 409) return "yellow" as const;
  return "red" as const;
}

/**
 * 37단계: 감사 로그 — 누가 언제 무엇을 바꿨는지(실패 포함)와 로그인 기록. 사용자·대상으로 거른다.
 * 입력 중에는 조회하지 않고 Enter나 입력란을 벗어날 때 반영한다.
 */
export function AuditLogsPage() {
  const [actorInput, setActorInput] = useState("");
  const [targetIdInput, setTargetIdInput] = useState("");
  const [actorUsername, setActorUsername] = useState("");
  const [targetType, setTargetType] = useState("");
  const [targetId, setTargetId] = useState("");
  const [page, setPage] = useState(0);

  const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  if (actorUsername) params.set("actorUsername", actorUsername);
  if (targetType) params.set("targetType", targetType);
  if (targetType && targetId) params.set("targetId", targetId);

  const logsQuery = useQuery({
    queryKey: ["audit-logs", actorUsername, targetType, targetId, page],
    queryFn: () => api.get<PageResponse<AuditLogResponse>>(`/api/admin/audit-logs?${params.toString()}`),
    placeholderData: keepPreviousData,
  });

  const applyText = () => {
    setActorUsername(actorInput.trim());
    setTargetId(/^\d+$/.test(targetIdInput.trim()) ? targetIdInput.trim() : "");
    setPage(0);
  };

  return (
    <div>
      <PageHeader title="감사 로그" />
      <Card className="mb-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="w-48">
            <Input
              placeholder="사용자 아이디"
              value={actorInput}
              onChange={(e) => setActorInput(e.target.value)}
              onBlur={applyText}
              onKeyDown={(e) => e.key === "Enter" && applyText()}
              aria-label="사용자 아이디"
            />
          </div>
          <div className="w-40">
            <Select
              value={targetType}
              onChange={(e) => {
                setTargetType(e.target.value);
                setPage(0);
              }}
              aria-label="대상 종류"
            >
              <option value="">전체 대상</option>
              {AUDIT_TARGET_TYPES.map((t) => (
                <option key={t.value} value={t.value}>
                  {t.label}
                </option>
              ))}
            </Select>
          </div>
          <div className="w-32">
            <Input
              placeholder="대상 ID"
              inputMode="numeric"
              value={targetIdInput}
              disabled={!targetType}
              onChange={(e) => setTargetIdInput(e.target.value)}
              onBlur={applyText}
              onKeyDown={(e) => e.key === "Enter" && applyText()}
              aria-label="대상 ID"
            />
          </div>
          <span className="text-xs text-gray-400">상태를 바꾸는 모든 요청(실패 포함)과 로그인 결과가 남습니다. 요청 본문은 저장하지 않습니다.</span>
        </div>
      </Card>

      <ErrorBanner message={logsQuery.error ? errorMessage(logsQuery.error) : null} />
      {logsQuery.isLoading && <Spinner />}
      {logsQuery.data && (
        <Card>
          <Table>
            <thead>
              <tr>
                <Th>시각</Th>
                <Th>사용자</Th>
                <Th>동작</Th>
                <Th>대상</Th>
                <Th>결과</Th>
                <Th>IP</Th>
              </tr>
            </thead>
            <tbody>
              {logsQuery.data.content.map((log) => {
                const targetPath = auditTargetPath(log.targetType, log.targetId);
                return (
                  <tr key={log.id}>
                    <Td className="whitespace-nowrap text-xs tabular-nums text-gray-600">{new Date(log.createdAt).toLocaleString()}</Td>
                    <Td>
                      {log.actorUsername ? (
                        <button
                          type="button"
                          className="text-indigo-600 hover:underline"
                          onClick={() => {
                            setActorInput(log.actorUsername ?? "");
                            setActorUsername(log.actorUsername ?? "");
                            setPage(0);
                          }}
                          title="이 사용자로 거르기"
                        >
                          {log.actorUsername}
                        </button>
                      ) : (
                        <span className="text-gray-400">-</span>
                      )}
                    </Td>
                    <Td>
                      <div>{auditActionLabel(log.action)}</div>
                      {log.payload && <div className="max-w-md truncate font-mono text-[11px] text-gray-400" title={log.payload}>{log.payload}</div>}
                    </Td>
                    <Td className="whitespace-nowrap">
                      {log.targetType && log.targetId !== null ? (
                        targetPath ? (
                          <Link to={targetPath} className="text-indigo-600 hover:underline">
                            {auditTargetLabel(log.targetType)} #{log.targetId}
                          </Link>
                        ) : (
                          `${auditTargetLabel(log.targetType)} #${log.targetId}`
                        )
                      ) : (
                        <span className="text-gray-400">-</span>
                      )}
                    </Td>
                    <Td>
                      <Badge tone={statusTone(log.httpStatus)}>{log.httpStatus ?? "-"}</Badge>
                    </Td>
                    <Td className="font-mono text-xs text-gray-500">{log.ipAddress ?? "-"}</Td>
                  </tr>
                );
              })}
              {logsQuery.data.content.length === 0 && (
                <tr>
                  <Td className="text-gray-500">조건에 맞는 기록이 없습니다.</Td>
                </tr>
              )}
            </tbody>
          </Table>
          <Pagination page={logsQuery.data} onPageChange={setPage} />
        </Card>
      )}
    </div>
  );
}
