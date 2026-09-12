import { Link } from "react-router";
import { Badge, Card, Table, Td, Th } from "../ui";
import type { UserResponse } from "../../types/api";

interface UserTableProps {
  users: UserResponse[];
}

export function UserTable({ users }: UserTableProps) {
  return (
    <Card>
      <Table>
        <thead>
          <tr>
            <Th>아이디</Th>
            <Th>이름</Th>
            <Th>역할</Th>
            <Th>상태</Th>
            <Th />
          </tr>
        </thead>
        <tbody>
          {users.map((user) => (
            <tr key={user.id}>
              <Td>{user.username}</Td>
              <Td>{user.displayName}</Td>
              <Td>
                <Badge tone={user.roleCode === "ADMIN" ? "blue" : "gray"}>{user.roleCode}</Badge>
              </Td>
              <Td>
                <Badge tone={user.status === "ACTIVE" ? "green" : "red"}>{user.status}</Badge>
              </Td>
              <Td>
                <Link to={`/users/${user.id}`} className="text-indigo-600 hover:underline">
                  작업장 권한 관리
                </Link>
              </Td>
            </tr>
          ))}
        </tbody>
      </Table>
    </Card>
  );
}
