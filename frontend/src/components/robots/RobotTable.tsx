import { labels } from "../../lib/labels";
import { Link } from "react-router";
import { Badge, Card, Table, Td, Th } from "../ui";
import type { RobotResponse, SiteResponse } from "../../types/api";

interface RobotTableProps {
  robots: RobotResponse[];
  sites: SiteResponse[];
}

export function RobotTable({ robots, sites }: RobotTableProps) {
  const siteCodeOf = (id: number) => sites.find((s) => s.id === id)?.code ?? id;

  return (
    <Card>
      <Table>
        <thead>
          <tr>
            <Th>코드</Th>
            <Th>이름</Th>
            <Th>물류센터</Th>
            <Th>운영 상태</Th>
            <Th>설정(요청/적용)</Th>
            <Th />
          </tr>
        </thead>
        <tbody>
          {robots.map((robot) => (
            <tr key={robot.id}>
              <Td>{robot.code}</Td>
              <Td>{robot.name}</Td>
              <Td>{siteCodeOf(robot.siteId)}</Td>
              <Td>
                <Badge tone={robot.lifecycleStatus === "IN_SERVICE" ? "green" : "gray"}>{labels.robotLifecycle(robot.lifecycleStatus)}</Badge>
              </Td>
              <Td>
                v{robot.desiredConfigVersion ?? "-"} / v{robot.appliedConfigVersion ?? "-"}
              </Td>
              <Td>
                <Link to={`/robots/${robot.id}`} className="text-indigo-600 hover:underline">
                  상세
                </Link>
              </Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
