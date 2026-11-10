package ai.pleosapp.drive.ui.common

import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import ai.pleosapp.drive.R
import androidx.compose.ui.geometry.Offset

/**
 * TEMP: 지도 사진 위에 있는 코스 선들. 사진 크기를 1로 본 좌표예요(좌상단 0,0).
 * 선이 여러 갈래면 사이에 [Offset.Unspecified] 를 넣어 "펜을 뗀 곳"을 나타내요.
 * 진짜 코스는 서버/ViewModel(C·D)이 주니까 그때 지워요.
 */
object SampleRoute {
    private fun pts(vararg xy: Float): List<Offset> = xy.toList().chunked(2).map { Offset(it[0], it[1]) }

    private fun join(vararg strokes: List<Offset>): List<Offset> =
        strokes.flatMapIndexed { i, l -> if (i == 0) l else listOf(Offset.Unspecified) + l }

    /** 사진 안의 경로(펜을 뗀 곳 제외)만 */
    fun List<Offset>.drawable(): List<Offset> = filter { it.isSpecified }

    /** 코스 시작점 = 선의 첫 점 */
    fun startOf(route: List<Offset>): Offset = route.first { it.isSpecified }

    /** 현재 위치(시안: 시작점 왼쪽 아래) */
    fun currentNear(start: Offset): Offset = Offset((start.x - 0.1f).coerceAtLeast(0.03f), (start.y + 0.065f).coerceAtMost(0.97f))

    /** 시작점을 기준으로 줄인 같은 모양 (짧은 코스용 임시값) */
    fun scaled(route: List<Offset>, factor: Float): List<Offset> {
        val s = startOf(route)
        return route.map { if (it.isSpecified) Offset(s.x + (it.x - s.x) * factor, s.y + (it.y - s.y) * factor) else it }
    }

    // ---- B 하트 코스 (수원) ----
    val HEART_MAP = R.drawable.map_suwon

    val heart: List<Offset> = listOf(
        0.4617f to 0.9209f, 0.447f to 0.9136f, 0.4342f to 0.899f, 0.4215f to 0.8863f, 0.4102f to 0.8708f, 0.3977f to 0.8591f,
        0.3857f to 0.8464f, 0.374f to 0.8328f, 0.3619f to 0.8196f, 0.3514f to 0.8036f, 0.3425f to 0.7854f, 0.3338f to 0.7669f,
        0.3296f to 0.7444f, 0.3249f to 0.7231f, 0.3167f to 0.7042f, 0.3089f to 0.6843f, 0.3009f to 0.6638f, 0.2943f to 0.6418f,
        0.2855f to 0.6192f, 0.273f to 0.595f, 0.2561f to 0.5679f, 0.2262f to 0.5344f, 0.2094f to 0.4978f, 0.2024f to 0.4606f,
        0.1978f to 0.4216f, 0.1989f to 0.3834f, 0.2034f to 0.3459f, 0.2098f to 0.3083f, 0.2106f to 0.2628f, 0.2129f to 0.2148f,
        0.216f to 0.1626f, 0.2332f to 0.1272f, 0.3004f to 0.1857f, 0.3736f to 0.2844f, 0.4029f to 0.3095f, 0.4214f to 0.3156f,
        0.4393f to 0.3262f, 0.4547f to 0.3346f, 0.4657f to 0.3253f, 0.4764f to 0.3113f, 0.4884f to 0.3005f, 0.5012f to 0.2913f,
        0.5147f to 0.2833f, 0.5288f to 0.2777f, 0.5437f to 0.2719f, 0.5588f to 0.2698f, 0.5742f to 0.2698f, 0.5904f to 0.2697f,
        0.6073f to 0.2698f, 0.6251f to 0.2708f, 0.6429f to 0.2749f, 0.6604f to 0.2823f, 0.6773f to 0.2931f, 0.6943f to 0.3053f,
        0.7137f to 0.3163f, 0.7381f to 0.3241f, 0.7711f to 0.3275f, 0.8033f to 0.3381f, 0.8081f to 0.3744f, 0.7993f to 0.4175f,
        0.776f to 0.4633f, 0.7568f to 0.5009f, 0.7444f to 0.5315f, 0.7336f to 0.5585f, 0.7209f to 0.5829f, 0.7117f to 0.6049f,
        0.6972f to 0.6239f, 0.6873f to 0.6416f, 0.6804f to 0.6587f, 0.6748f to 0.6758f, 0.6704f to 0.6932f, 0.6649f to 0.7098f,
        0.6575f to 0.7246f, 0.6509f to 0.7399f, 0.6418f to 0.7522f, 0.6319f to 0.7627f, 0.6218f to 0.772f, 0.6096f to 0.7766f,
        0.5985f to 0.7815f, 0.5891f to 0.7892f, 0.582f to 0.8036f, 0.5746f to 0.8196f, 0.566f to 0.8346f, 0.5568f to 0.8518f,
        0.547f to 0.8731f, 0.5346f to 0.8828f, 0.5214f to 0.8869f, 0.5079f to 0.8892f, 0.4939f to 0.8956f, 0.4784f to 0.9099f,
        0.4617f to 0.9209f,
    ).map { Offset(it.first, it.second) }

    // ---- A 표범 코스 (기흥 호수 근처): 바깥 선 + 안쪽 점무늬 ----
    val LEOPARD_MAP = R.drawable.map_leopard
    val leopard: List<Offset> = join(
        pts(
            0.6213f, 0.7410f, 0.6064f, 0.7410f, 0.5617f, 0.7179f, 0.4894f, 0.7154f, 0.4426f, 0.6718f, 0.3489f, 0.6256f,
            0.3106f, 0.5769f, 0.2915f, 0.5590f, 0.2809f, 0.5256f, 0.2383f, 0.4821f, 0.2298f, 0.4615f, 0.1723f, 0.4051f,
            0.1638f, 0.3795f, 0.1638f, 0.3590f, 0.1745f, 0.3308f, 0.1915f, 0.3128f, 0.2298f, 0.3103f, 0.2511f, 0.3026f,
            0.3596f, 0.2949f, 0.4340f, 0.2718f, 0.4851f, 0.2641f, 0.5213f, 0.2462f, 0.6085f, 0.2410f, 0.6149f, 0.2205f,
            0.6213f, 0.1615f, 0.6340f, 0.1385f, 0.6723f, 0.1385f, 0.7298f, 0.1744f, 0.7447f, 0.1923f, 0.7574f, 0.2359f,
            0.7426f, 0.2590f, 0.7383f, 0.2744f, 0.7362f, 0.4308f, 0.7128f, 0.4718f, 0.7085f, 0.6744f, 0.7021f, 0.6949f,
            0.6894f, 0.7077f, 0.6404f, 0.7359f, 0.6234f, 0.7410f,
        ),
        pts(
            0.5085f, 0.5974f, 0.5213f, 0.5949f, 0.5894f, 0.5487f, 0.6191f, 0.5359f, 0.6362f, 0.5231f, 0.6511f, 0.5256f,
            0.6511f, 0.5590f, 0.6404f, 0.5590f, 0.6213f, 0.5385f,
        ),
        pts(
            0.3787f, 0.4615f, 0.4191f, 0.4641f, 0.4617f, 0.4590f, 0.4681f, 0.4641f, 0.4979f, 0.4513f,
        ),
        pts(
            0.5830f, 0.4256f, 0.5979f, 0.4077f, 0.6638f, 0.3590f,
        ),
        pts(
            0.5936f, 0.4231f, 0.6255f, 0.4205f, 0.6319f, 0.4103f, 0.6340f, 0.3897f,
        ),
        pts(
            0.4213f, 0.4692f, 0.4340f, 0.4821f, 0.4489f, 0.4821f, 0.4638f, 0.4692f,
        ),
    )

    // ---- C 촛불 코스 (용인) ----
    val CANDLE_MAP = R.drawable.map_candle
    val candle: List<Offset> = join(
        pts(
            0.7383f, 0.7833f, 0.7039f, 0.7759f, 0.6856f, 0.7660f, 0.6329f, 0.7635f, 0.5720f, 0.7414f, 0.5456f, 0.7389f,
            0.5254f, 0.7192f, 0.5112f, 0.7167f, 0.4848f, 0.7020f, 0.4706f, 0.6847f, 0.4625f, 0.6626f, 0.4300f, 0.6305f,
            0.4361f, 0.6108f, 0.4361f, 0.5764f, 0.4341f, 0.5665f, 0.4199f, 0.5468f, 0.3692f, 0.5369f, 0.3144f, 0.5197f,
            0.3083f, 0.5123f, 0.3124f, 0.4852f, 0.3002f, 0.4729f, 0.2799f, 0.4754f, 0.2515f, 0.4926f, 0.2414f, 0.4901f,
            0.2211f, 0.4581f, 0.2049f, 0.4483f, 0.1826f, 0.4261f, 0.1359f, 0.3645f, 0.1379f, 0.2980f, 0.1440f, 0.2808f,
            0.1400f, 0.2463f, 0.1623f, 0.2167f, 0.1684f, 0.2167f, 0.2110f, 0.2463f, 0.2617f, 0.2709f, 0.2961f, 0.3276f,
            0.3063f, 0.3374f, 0.3043f, 0.3842f, 0.2921f, 0.4236f, 0.2921f, 0.4704f,
        ),
        pts(
            0.3083f, 0.3325f, 0.3306f, 0.3030f, 0.3509f, 0.2931f, 0.3773f, 0.2709f, 0.3874f, 0.2709f, 0.4118f, 0.2931f,
            0.4138f, 0.3079f, 0.4239f, 0.3300f, 0.4260f, 0.3522f, 0.4320f, 0.3645f, 0.4970f, 0.4360f, 0.5355f, 0.4631f,
            0.5659f, 0.5123f, 0.6207f, 0.5296f, 0.6450f, 0.5296f, 0.6531f, 0.5222f, 0.6613f, 0.5222f, 0.6734f, 0.5345f,
            0.6775f, 0.5542f, 0.6836f, 0.5640f, 0.7018f, 0.5665f, 0.7363f, 0.6034f, 0.7485f, 0.6059f, 0.7870f, 0.5887f,
            0.7972f, 0.6133f, 0.8053f, 0.6921f, 0.8195f, 0.7291f, 0.8073f, 0.7512f, 0.7748f, 0.7635f, 0.7424f, 0.7833f,
        ),
    )
}
