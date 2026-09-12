import { Link } from "react-router";
import { Badge, Table, Td, Th } from "../ui";
import type { MapVersionResponse } from "../../types/api";

interface VersionTableProps {
  mapId: string | undefined;
  versions: MapVersionResponse[];
}

function statusTone(status: string) {
  return status === "ACTIVE" ? "green" : status === "VALIDATED" ? "blue" : status === "ARCHIVED" ? "gray" : "yellow";
}

export function VersionTable({ mapId, versions }: VersionTableProps) {
  return (
    <Table>
      <thead>
        <tr>
          <Th>버전</Th>
          <Th>상태</Th>
          <Th>노드/통로/구역</Th>
          <Th />
        </tr>
      </thead>
      <tbody>
        {versions.map((version) => (
          <tr key={version.id}>
            <Td>v{version.versionNo}</Td>
            <Td>
              <Badge tone={statusTone(version.status)}>{version.status}</Badge>
            </Td>
            <Td>
              {version.nodeCount} / {version.edgeCount} / {version.zoneCount}
            </Td>
            <Td>
              <Link to={`/maps/${mapId}/versions/${version.id}`} className="text-indigo-600 hover:underline">
                편집
              </Link>
            </Td>
          </tr>
        ))}
      </tbody>
    </Table>
  );
}
