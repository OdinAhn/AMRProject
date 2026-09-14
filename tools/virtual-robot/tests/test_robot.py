"""가상 로봇 로직 단위 테스트(브로커·서버 불필요): python -m unittest discover -s tests"""

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from robot import RobotConfig, VirtualRobot  # noqa: E402
from route_map import RouteMap  # noqa: E402

# 서버 API 응답 모양: N1(0,0) —(양방향)— N2(100,0) —(N2→N3 한 방향)— N3(200,0)
NODES = [
    {"id": 1, "code": "N1", "x": 0, "y": 0},
    {"id": 2, "code": "N2", "x": 100, "y": 0},
    {"id": 3, "code": "N3", "x": 200, "y": 0},
]
EDGES = [
    {"fromNodeId": 1, "toNodeId": 2, "direction": "TWO_WAY", "distance": None},
    {"fromNodeId": 2, "toNodeId": 3, "direction": "ONE_WAY", "distance": None},
]


def route_map() -> RouteMap:
    return RouteMap.from_api(7, NODES, EDGES)


def order(order_id: str, node_id: str, actions=None) -> dict:
    return {"orderId": order_id, "orderUpdateId": 0,
            "nodes": [{"nodeId": node_id, "sequenceId": 0, "released": True, "actions": actions or []}]}


def run(robot: VirtualRobot, seconds: float, dt: float = 0.1) -> None:
    for _ in range(int(round(seconds / dt))):
        robot.tick(dt)


def finished(state: dict) -> bool:
    """서버(Vda5050State.orderFinished)와 같은 종료 규칙."""
    if state["errors"]:
        return True
    running = [a for a in state["actionStates"] if a["actionStatus"] not in ("FINISHED", "FAILED")]
    return not state["driving"] and not state["nodeStates"] and not running


class RouteMapTest(unittest.TestCase):
    def test_shortest_path_respects_one_way(self):
        graph = route_map()
        self.assertEqual(graph.shortest_path("N1", "N3"), ["N1", "N2", "N3"])
        self.assertIsNone(graph.shortest_path("N3", "N1"))
        self.assertEqual(graph.shortest_path("N2", "N2"), ["N2"])


class VirtualRobotTest(unittest.TestCase):
    def setUp(self):
        self.robot = VirtualRobot("VR-01", "AMRProject", route_map(), start_node="N1", battery=80.0,
                                  config=RobotConfig(speed=50.0, drain_per_unit=0.01, charge_per_sec=10.0))

    def state(self) -> dict:
        return self.robot.state_message(1, "2026-09-14T00:00:00Z")

    def test_moves_along_corridor_and_reports_order_finished_only_on_arrival(self):
        self.robot.handle_order(order("o1", "N3"))
        state = self.state()
        self.assertTrue(state["driving"])
        self.assertEqual([n["nodeId"] for n in state["nodeStates"]], ["N2", "N3"])
        self.assertFalse(finished(state))

        run(self.robot, 2.2)  # 50/초로 100 이동 → N2 통과
        self.assertEqual(self.state()["lastNodeId"], "N2")
        self.assertFalse(finished(self.state()))

        run(self.robot, 2.2)
        state = self.state()
        self.assertEqual(state["lastNodeId"], "N3")
        self.assertEqual(state["agvPosition"]["x"], 200)
        self.assertTrue(finished(state))
        self.assertAlmostEqual(state["batteryState"]["batteryCharge"], 78.0, places=1)  # 200 × 0.01

    def test_charging_action_keeps_order_open_until_full(self):
        self.robot.handle_order(order("c1", "N1", [{"actionType": "startCharging", "actionId": "c1", "blockingType": "HARD"}]))
        run(self.robot, 0.5)
        state = self.state()
        self.assertTrue(state["batteryState"]["charging"])
        self.assertFalse(state["driving"])
        self.assertFalse(finished(state))  # 멈춰 있지만 충전 중이라 끝나지 않았다

        run(self.robot, 2.5)
        state = self.state()
        self.assertEqual(state["batteryState"]["batteryCharge"], 100.0)
        self.assertEqual(state["actionStates"][0]["actionStatus"], "FINISHED")
        self.assertTrue(finished(state))

    def test_rejects_unreachable_target_and_when_manual_or_stopped(self):
        self.robot.handle_order(order("o1", "N3"))
        run(self.robot, 5)
        self.robot.handle_order(order("o2", "N1"))  # N3 → N1은 한 방향 통로라 갈 수 없다
        self.assertEqual(self.state()["errors"][0]["errorType"], "noRouteError")
        self.assertTrue(finished(self.state()))

        self.robot.operating_mode = "MANUAL"
        self.robot.handle_order(order("o3", "N3"))
        self.assertEqual(self.state()["errors"][0]["errorType"], "orderError")

        self.robot.operating_mode = "AUTOMATIC"
        self.robot.press_emergency_stop()
        self.robot.handle_order(order("o4", "N3"))
        self.assertEqual(self.state()["safetyState"]["eStop"], "MANUAL")
        self.assertEqual(self.state()["errors"][0]["errorType"], "orderError")

    def test_start_pause_stops_and_latches_remote_estop_until_release(self):
        self.robot.handle_order(order("o1", "N3"))
        run(self.robot, 1)
        self.robot.handle_instant_actions({"actions": [{"actionType": "startPause", "actionId": "p1"}]})
        position = self.state()["agvPosition"]["x"]
        run(self.robot, 2)
        state = self.state()
        self.assertEqual(state["agvPosition"]["x"], position)
        self.assertTrue(state["paused"])
        self.assertEqual(state["safetyState"]["eStop"], "REMOTE")
        self.assertIn({"actionId": "p1", "actionType": "startPause", "actionStatus": "FINISHED"}, state["actionStates"])
        self.assertFalse(finished(state))  # 주문은 멈춰 있을 뿐 끝나지 않았다

        self.robot.handle_instant_actions({"actions": [{"actionType": "stopPause", "actionId": "p2"}]})
        self.assertEqual(self.state()["actionStates"][-1]["actionStatus"], "FAILED")  # 원격 비상정지는 현장 해제 전엔 못 푼다
        self.robot.release_emergency_stop()
        run(self.robot, 5)
        self.assertEqual(self.state()["lastNodeId"], "N3")

    def test_cancel_order_stops_and_finishes_order(self):
        self.robot.handle_order(order("o1", "N3"))
        run(self.robot, 1)
        self.robot.handle_instant_actions({"actions": [{"actionType": "cancelOrder", "actionId": "x1"}]})
        run(self.robot, 3)
        state = self.state()
        self.assertTrue(finished(state))
        self.assertLess(state["agvPosition"]["x"], 200)
        self.assertEqual(state["actionStates"][-1]["actionStatus"], "FINISHED")

    def test_unknown_target_travels_by_time(self):
        robot = VirtualRobot("VR-02", "AMRProject", None, config=RobotConfig(travel_seconds=1.0))
        robot.handle_order(order("o1", "SOME-STATION"))
        self.assertFalse(finished(robot.state_message(1, "t")))
        run(robot, 1.2)
        state = robot.state_message(2, "t")
        self.assertEqual(state["lastNodeId"], "SOME-STATION")
        self.assertTrue(finished(state))

    def test_position_lost_and_loads_are_reported_and_lost_robot_rejects_orders(self):
        self.robot.position_lost = True
        self.robot.loads = [{"loadId": "L1", "loadType": "PALLET"}]
        state = self.state()
        self.assertFalse(state["agvPosition"]["positionInitialized"])
        self.assertEqual(state["loads"], [{"loadId": "L1", "loadType": "PALLET"}])
        self.robot.handle_order(order("o1", "N3"))
        self.assertEqual(self.state()["errors"][0]["errorType"], "orderError")

        self.robot.position_lost = False
        self.robot.handle_order(order("o2", "N2"))
        self.assertTrue(self.state()["agvPosition"]["positionInitialized"])
        self.assertEqual(self.state()["errors"], [])

    def test_silent_robot_ignores_orders(self):
        self.robot.silent = True
        self.assertFalse(self.robot.handle_order(order("o1", "N3")))
        self.assertEqual(self.state()["orderId"], "")


if __name__ == "__main__":
    unittest.main()
