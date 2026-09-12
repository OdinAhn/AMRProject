import { Link } from "react-router";
import { Card, Table, Td, Th } from "../ui";
import type { MapResponse } from "../../types/api";

interface MapTableProps {
  maps: MapResponse[];
}

export function MapTable({ maps }: MapTableProps) {
  return (
    <Card>
      <Table>
        <thead>
          <tr>
            <Th>이름</Th>
            <Th />
          </tr>
        </thead>
        <tbody>
          {maps.map((map) => (
            <tr key={map.id}>
              <Td>{map.name}</Td>
              <Td>
                <Link to={`/maps/${map.id}`} className="text-indigo-600 hover:underline">
                  버전 관리
                </Link>
              </Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
