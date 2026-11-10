package ai.pleosapp.drive.core.navi

/** 화면에 보여 줄 회전 종류. 화살표 그림은 E가 이 값으로 고른다. */
enum class ManeuverKind {
    STRAIGHT, SLIGHT_LEFT, LEFT, SHARP_LEFT, SLIGHT_RIGHT, RIGHT, SHARP_RIGHT, U_TURN, ROUNDABOUT, ARRIVE,
}

/**
 * 운전자에게 알릴 안내 한 개.
 * [text]는 짧은 한글(예: "좌회전"). "450m 앞에서 좌회전"처럼 문장으로 만드는 것은 화면(E)이 한다.
 * [atM]은 구간 경로를 따라 잰 안내 지점 위치.
 */
data class Maneuver(
    val kind: ManeuverKind,
    val text: String,
    val roadName: String,
    val atM: Double,
)

/** OSRM 안내(type, modifier)를 한글 안내로 바꾼다. */
object ManeuverText {

    /** 알릴 필요가 없는 안내(출발, 도로 이름만 바뀜, 그냥 직진 등)는 null. */
    fun of(step: RouteStep): Maneuver? {
        val type = step.type
        val mod = step.modifier
        val road = step.name
        fun m(kind: ManeuverKind, text: String) = Maneuver(kind, text, road, step.atM)

        return when (type) {
            "depart", "new name", "notification", "exit roundabout", "exit rotary" -> null
            "arrive" -> m(ManeuverKind.ARRIVE, "목적지 도착")
            "roundabout", "rotary" ->
                m(ManeuverKind.ROUNDABOUT, step.exit?.let { "회전교차로 ${it}번째 출구" } ?: "회전교차로")
            // 작은 회전교차로: 출구 대신 방향으로 안내한다.
            "roundabout turn" -> direction(mod)?.let { (_, text) -> m(ManeuverKind.ROUNDABOUT, "회전교차로 $text") }
                ?: m(ManeuverKind.ROUNDABOUT, "회전교차로")
            "continue" -> when (mod) {
                // 길 따라 그대로 가는 것은 알리지 않는다.
                "straight", "" -> null
                // 길이 휘어 이어지는 경우라 "방향"으로 부드럽게 알린다.
                "left" -> m(ManeuverKind.SLIGHT_LEFT, "왼쪽 방향")
                "right" -> m(ManeuverKind.SLIGHT_RIGHT, "오른쪽 방향")
                else -> direction(mod)?.let { (k, t) -> m(k, t) }
            }
            "turn" -> if (mod == "straight" || mod.isEmpty()) null else direction(mod)?.let { (k, t) -> m(k, t) }
            // 갈림길·합류·진입로·막다른 길(T자)은 직진이라도 알린다.
            "fork", "merge", "on ramp", "off ramp", "end of road" ->
                direction(mod.ifEmpty { "straight" })?.let { (k, t) -> m(k, t) }
            else -> direction(mod)?.takeIf { mod != "straight" && mod.isNotEmpty() }?.let { (k, t) -> m(k, t) }
        }
    }

    private fun direction(modifier: String): Pair<ManeuverKind, String>? = when (modifier) {
        "straight" -> ManeuverKind.STRAIGHT to "직진"
        "slight left" -> ManeuverKind.SLIGHT_LEFT to "왼쪽 방향"
        "left" -> ManeuverKind.LEFT to "좌회전"
        "sharp left" -> ManeuverKind.SHARP_LEFT to "급좌회전"
        "slight right" -> ManeuverKind.SLIGHT_RIGHT to "오른쪽 방향"
        "right" -> ManeuverKind.RIGHT to "우회전"
        "sharp right" -> ManeuverKind.SHARP_RIGHT to "급우회전"
        "uturn" -> ManeuverKind.U_TURN to "유턴"
        else -> null
    }
}
