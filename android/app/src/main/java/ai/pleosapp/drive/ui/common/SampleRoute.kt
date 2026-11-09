package ai.pleosapp.drive.ui.common

import androidx.compose.ui.geometry.Offset

/**
 * TEMP: 지도 사진(map_suwon) 위에 있는 B 하트 코스 선. 사진 크기를 1로 본 좌표예요.
 * 진짜 코스는 서버/ViewModel(C·D)이 주니까 그때 지워요.
 */
object SampleRoute {
    /** 코스 시작점 (사진 아래쪽) */
    val start = Offset(0.4617f, 0.9209f)

    /** 현재 위치(시안: 시작점 왼쪽 아래) */
    val currentPosition = Offset(0.355f, 0.985f)

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

    /** 시작점을 기준으로 줄인 같은 모양 (짧은 코스용 임시값) */
    fun scaled(factor: Float): List<Offset> =
        heart.map { Offset(start.x + (it.x - start.x) * factor, start.y + (it.y - start.y) * factor) }
}
