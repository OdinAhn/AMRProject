import { Link } from "react-router";
import { Badge, Card, Table, Td, Th } from "../ui";
import type { SiteResponse } from "../../types/api";

interface SiteTableProps {
  sites: SiteResponse[];
}

export function SiteTable({ sites }: SiteTableProps) {
  return (
    <Card>
      <Table>
        <thead>
          <tr>
            <Th>코드</Th>
            <Th>이름</Th>
            <Th>상태</Th>
            <Th />
          </tr>
        </thead>
        <tbody>
          {sites.map((site) => (
            <tr key={site.id}>
              <Td>{site.code}</Td>
              <Td>{site.name}</Td>
              <Td>
                <Badge tone={site.status === "ACTIVE" ? "green" : "gray"}>{site.status}</Badge>
              </Td>
              <Td>
                <Link to={`/sites/${site.id}`} className="text-indigo-600 hover:underline">
                  작업장 관리
                </Link>
              </Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
