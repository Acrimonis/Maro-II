package ykws.android.maro.route

import ykws.android.maro.spatial.SpatialOperations
import java.util.PriorityQueue

/**
 * A* over the visibility graph, **with the entry direction in the state** — plan §11.5's turn-aware
 * state space, which §12.3 then fills with a real price instead of a hand-tuned `k_turn`: the cost of
 * a heading change is the time the corner actually costs, arc plus brake-and-accelerate.
 *
 * The state is a *directed edge*: arriving at a vertex along a particular leg. Expanding it prices the
 * corner between that leg and each outgoing leg, so a state count of `2 × edges` (thousands) rather
 * than a search that cannot see corners at all (which would prefer the shorter, slower corner and
 * invert the stated priority).
 *
 * `h` is the straight-line distance to the destination ÷ cruise speed. It is admissible — every leg is
 * driven at most at cruise, and every corner and price only adds time — and consistent, so the first
 * goal state popped is optimal and the tie-break can be deterministic: `f`, then `g`, then state id,
 * which is what lets a test assert an exact path (§6).
 */
object RouteSearch {

    /** The virtual state the search starts from: at the start vertex, with no entry direction. */
    private const val START_STATE = -1

    private const val UNVISITED = -2

    private data class Entry(val f: Double, val g: Double, val state: Int)

    fun search(
        graph: RouteGraph,
        startIndex: Int,
        goalIndex: Int,
        config: RouteConfig,
        context: RouteContext,
        obstacles: RouteObstacles,
    ): RoutePath? {
        if (startIndex == goalIndex) {
            return RoutePath(listOf(startIndex), listOf(graph.points[startIndex]), 0.0, 0.0, 0)
        }

        val gScore = DoubleArray(graph.directedCount) { Double.POSITIVE_INFINITY }
        val parent = IntArray(graph.directedCount) { UNVISITED }
        val cornerCost = HashMap<Long, Double>()
        var expanded = 0

        fun heuristic(vertex: Int): Double =
            SpatialOperations.haversine(graph.points[vertex], graph.points[goalIndex]) / config.cruiseMps

        val queue = PriorityQueue(compareBy<Entry>({ it.f }, { it.g }, { it.state }))

        for (d in graph.outEdges[startIndex]) {
            val g = graph.directedTimeS(d)
            if (g < gScore[d]) {
                gScore[d] = g
                parent[d] = START_STATE
                queue.add(Entry(g + heuristic(graph.toVertex[d]), g, d))
            }
        }

        while (queue.isNotEmpty()) {
            val entry = queue.poll() ?: break
            val state = entry.state
            if (entry.g > gScore[state]) continue // a stale queue entry
            expanded++
            val vertex = graph.toVertex[state]

            if (vertex == goalIndex) {
                return reconstruct(graph, state, parent, gScore[state], expanded)
            }

            val incomingSpeed = graph.edges[state / 2].speedMps
            val incomingLength = graph.edges[state / 2].lengthM
            for (next in graph.outEdges[vertex]) {
                val key = state.toLong() * graph.directedCount.toLong() + next.toLong()
                val corner = cornerCost.getOrPut(key) {
                    RouteFillet.plan(
                        prev = graph.points[graph.fromVertex[state]],
                        corner = graph.points[vertex],
                        next = graph.points[graph.toVertex[next]],
                        prevSpeedMps = incomingSpeed,
                        nextSpeedMps = graph.edges[next / 2].speedMps,
                        prevLegM = incomingLength,
                        nextLegM = graph.edges[next / 2].lengthM,
                        config = config,
                        context = context,
                        obstacles = obstacles,
                    ).totalTimeS
                }
                val tentative = entry.g + corner + graph.directedTimeS(next)
                if (tentative < gScore[next] - 1e-12) {
                    gScore[next] = tentative
                    parent[next] = state
                    queue.add(Entry(tentative + heuristic(graph.toVertex[next]), tentative, next))
                }
            }
        }
        return null
    }

    private fun reconstruct(
        graph: RouteGraph,
        goalState: Int,
        parent: IntArray,
        totalTime: Double,
        expanded: Int,
    ): RoutePath {
        val states = ArrayList<Int>()
        var state = goalState
        while (state != START_STATE) {
            states.add(state)
            state = parent[state]
        }
        states.reverse()

        val vertices = ArrayList<Int>(states.size + 1)
        vertices.add(graph.fromVertex[states.first()])
        var edgeTime = 0.0
        for (s in states) {
            vertices.add(graph.toVertex[s])
            edgeTime += graph.directedTimeS(s)
        }
        return RoutePath(
            vertices = vertices,
            points = vertices.map { graph.points[it] },
            timeS = totalTime,
            edgeTimeS = edgeTime,
            nodesExpanded = expanded,
        )
    }

    /**
     * Dijkstra over the same graph with leg times only — no corners, no turns.
     *
     * This is the **reference** the tests check A* against on a small synthetic map (§13.3): it is a
     * plainly-written shortest-path routine, so agreement between the two is evidence about the
     * search rather than about the graph.
     */
    fun dijkstraEdgeTimeS(graph: RouteGraph, startIndex: Int, goalIndex: Int): Double {
        if (startIndex == goalIndex) return 0.0
        val best = DoubleArray(graph.vertexCount) { Double.POSITIVE_INFINITY }
        val settled = BooleanArray(graph.vertexCount)
        best[startIndex] = 0.0
        val queue = PriorityQueue(compareBy<Entry>({ it.g }, { it.f }))

        for (d in graph.outEdges[startIndex]) {
            val to = graph.toVertex[d]
            val time = graph.directedTimeS(d)
            if (time < best[to]) {
                best[to] = time
                queue.add(Entry(time, time, to))
            }
        }

        while (queue.isNotEmpty()) {
            val entry = queue.poll() ?: break
            val v = entry.state
            if (settled[v]) continue
            settled[v] = true
            if (v == goalIndex) return best[v]
            for (d in graph.outEdges[v]) {
                val to = graph.toVertex[d]
                val tentative = best[v] + graph.directedTimeS(d)
                if (tentative < best[to] - 1e-12) {
                    best[to] = tentative
                    queue.add(Entry(tentative, tentative, to))
                }
            }
        }
        return best[goalIndex]
    }
}
