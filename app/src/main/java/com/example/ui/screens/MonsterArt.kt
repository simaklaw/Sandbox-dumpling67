package com.example.ui.screens

import com.example.R

/** Таблица картинок конструктора монстриков (Kenney Monster Builder Pack, CC0). Файл создан скриптом. */
object MonsterArt {
    val BODY = arrayOf(
        intArrayOf(R.drawable.mb_body_blue_a, R.drawable.mb_body_blue_b, R.drawable.mb_body_blue_c, R.drawable.mb_body_blue_d, R.drawable.mb_body_blue_e, R.drawable.mb_body_blue_f),
        intArrayOf(R.drawable.mb_body_dark_a, R.drawable.mb_body_dark_b, R.drawable.mb_body_dark_c, R.drawable.mb_body_dark_d, R.drawable.mb_body_dark_e, R.drawable.mb_body_dark_f),
        intArrayOf(R.drawable.mb_body_green_a, R.drawable.mb_body_green_b, R.drawable.mb_body_green_c, R.drawable.mb_body_green_d, R.drawable.mb_body_green_e, R.drawable.mb_body_green_f),
        intArrayOf(R.drawable.mb_body_red_a, R.drawable.mb_body_red_b, R.drawable.mb_body_red_c, R.drawable.mb_body_red_d, R.drawable.mb_body_red_e, R.drawable.mb_body_red_f),
        intArrayOf(R.drawable.mb_body_white_a, R.drawable.mb_body_white_b, R.drawable.mb_body_white_c, R.drawable.mb_body_white_d, R.drawable.mb_body_white_e, R.drawable.mb_body_white_f),
        intArrayOf(R.drawable.mb_body_yellow_a, R.drawable.mb_body_yellow_b, R.drawable.mb_body_yellow_c, R.drawable.mb_body_yellow_d, R.drawable.mb_body_yellow_e, R.drawable.mb_body_yellow_f)
    )
    val ARM = arrayOf(
        intArrayOf(R.drawable.mb_arm_blue_a, R.drawable.mb_arm_blue_b, R.drawable.mb_arm_blue_c, R.drawable.mb_arm_blue_d, R.drawable.mb_arm_blue_e),
        intArrayOf(R.drawable.mb_arm_dark_a, R.drawable.mb_arm_dark_b, R.drawable.mb_arm_dark_c, R.drawable.mb_arm_dark_d, R.drawable.mb_arm_dark_e),
        intArrayOf(R.drawable.mb_arm_green_a, R.drawable.mb_arm_green_b, R.drawable.mb_arm_green_c, R.drawable.mb_arm_green_d, R.drawable.mb_arm_green_e),
        intArrayOf(R.drawable.mb_arm_red_a, R.drawable.mb_arm_red_b, R.drawable.mb_arm_red_c, R.drawable.mb_arm_red_d, R.drawable.mb_arm_red_e),
        intArrayOf(R.drawable.mb_arm_white_a, R.drawable.mb_arm_white_b, R.drawable.mb_arm_white_c, R.drawable.mb_arm_white_d, R.drawable.mb_arm_white_e),
        intArrayOf(R.drawable.mb_arm_yellow_a, R.drawable.mb_arm_yellow_b, R.drawable.mb_arm_yellow_c, R.drawable.mb_arm_yellow_d, R.drawable.mb_arm_yellow_e)
    )
    val LEG = arrayOf(
        intArrayOf(R.drawable.mb_leg_blue_a, R.drawable.mb_leg_blue_b, R.drawable.mb_leg_blue_c, R.drawable.mb_leg_blue_d, R.drawable.mb_leg_blue_e),
        intArrayOf(R.drawable.mb_leg_dark_a, R.drawable.mb_leg_dark_b, R.drawable.mb_leg_dark_c, R.drawable.mb_leg_dark_d, R.drawable.mb_leg_dark_e),
        intArrayOf(R.drawable.mb_leg_green_a, R.drawable.mb_leg_green_b, R.drawable.mb_leg_green_c, R.drawable.mb_leg_green_d, R.drawable.mb_leg_green_e),
        intArrayOf(R.drawable.mb_leg_red_a, R.drawable.mb_leg_red_b, R.drawable.mb_leg_red_c, R.drawable.mb_leg_red_d, R.drawable.mb_leg_red_e),
        intArrayOf(R.drawable.mb_leg_white_a, R.drawable.mb_leg_white_b, R.drawable.mb_leg_white_c, R.drawable.mb_leg_white_d, R.drawable.mb_leg_white_e),
        intArrayOf(R.drawable.mb_leg_yellow_a, R.drawable.mb_leg_yellow_b, R.drawable.mb_leg_yellow_c, R.drawable.mb_leg_yellow_d, R.drawable.mb_leg_yellow_e)
    )
    /** DETAIL[вид][цвет]: 0 антенна большая, 1 антенна малая, 2 ушки, 3 круглые ушки, 4 рога большие, 5 рога малые, 6 глаз на макушке */
    val DETAIL = arrayOf(
        intArrayOf(R.drawable.mb_detail_blue_antenna_large, R.drawable.mb_detail_dark_antenna_large, R.drawable.mb_detail_green_antenna_large, R.drawable.mb_detail_red_antenna_large, R.drawable.mb_detail_white_antenna_large, R.drawable.mb_detail_yellow_antenna_large),
        intArrayOf(R.drawable.mb_detail_blue_antenna_small, R.drawable.mb_detail_dark_antenna_small, R.drawable.mb_detail_green_antenna_small, R.drawable.mb_detail_red_antenna_small, R.drawable.mb_detail_white_antenna_small, R.drawable.mb_detail_yellow_antenna_small),
        intArrayOf(R.drawable.mb_detail_blue_ear, R.drawable.mb_detail_dark_ear, R.drawable.mb_detail_green_ear, R.drawable.mb_detail_red_ear, R.drawable.mb_detail_white_ear, R.drawable.mb_detail_yellow_ear),
        intArrayOf(R.drawable.mb_detail_blue_ear_round, R.drawable.mb_detail_dark_ear_round, R.drawable.mb_detail_green_ear_round, R.drawable.mb_detail_red_ear_round, R.drawable.mb_detail_white_ear_round, R.drawable.mb_detail_yellow_ear_round),
        intArrayOf(R.drawable.mb_detail_blue_horn_large, R.drawable.mb_detail_dark_horn_large, R.drawable.mb_detail_green_horn_large, R.drawable.mb_detail_red_horn_large, R.drawable.mb_detail_white_horn_large, R.drawable.mb_detail_yellow_horn_large),
        intArrayOf(R.drawable.mb_detail_blue_horn_small, R.drawable.mb_detail_dark_horn_small, R.drawable.mb_detail_green_horn_small, R.drawable.mb_detail_red_horn_small, R.drawable.mb_detail_white_horn_small, R.drawable.mb_detail_yellow_horn_small),
        intArrayOf(R.drawable.mb_detail_blue_eye, R.drawable.mb_detail_dark_eye, R.drawable.mb_detail_green_eye, R.drawable.mb_detail_red_eye, R.drawable.mb_detail_white_eye, R.drawable.mb_detail_yellow_eye)
    )
    val EYES = intArrayOf(R.drawable.mb_eye_blue, R.drawable.mb_eye_human, R.drawable.mb_eye_human_blue, R.drawable.mb_eye_human_green, R.drawable.mb_eye_human_red, R.drawable.mb_eye_cute_dark, R.drawable.mb_eye_cute_light, R.drawable.mb_eye_yellow, R.drawable.mb_eye_red, R.drawable.mb_eye_closed_happy)
    val MOUTHS = intArrayOf(R.drawable.mb_mouth_a, R.drawable.mb_mouth_b, R.drawable.mb_mouth_c, R.drawable.mb_mouth_d, R.drawable.mb_mouth_e, R.drawable.mb_mouth_f, R.drawable.mb_mouth_g, R.drawable.mb_mouth_h, R.drawable.mb_mouth_i, R.drawable.mb_mouth_j, R.drawable.mb_mouth_closed_happy, R.drawable.mb_mouth_closed_teeth, R.drawable.mb_mouth_closed_fangs)
    val NOSES = intArrayOf(R.drawable.mb_nose_red, R.drawable.mb_nose_green, R.drawable.mb_nose_yellow, R.drawable.mb_nose_brown)
}
