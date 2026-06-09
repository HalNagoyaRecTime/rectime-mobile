package com.rectime.mobile.feature.detail

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rectime.mobile.app.navigation.NavigationController
import com.rectime.mobile.app.navigation.Screen
import com.rectime.mobile.ui.component.PushScreenScaffold
import com.rectime.mobile.ui.theme.AppTheme

data class DetailScreen(val id: String) : Screen {
    override val key: String = "detail_$id"

    @Composable
    override fun Content(navigationController: NavigationController) {

        //競技のダミーデータ
        val kyougiName = "紙飛行機飛ばし"
        val kyougiDescription =
            "各自で作成した紙飛行機を当日持参し、決められた位置から一斉に飛ばします。"+
            "紙飛行機が停止した地点までの距離を計測し、最も遠くまで飛ばした参加者が優勝となります。"

        PushScreenScaffold(
            title = "競技詳細",
            onBack = { navigationController.requestPop() },
        ) {
            item {
                Text(
                    text = kyougiName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Text(
                    text = kyougiDescription,
                    color = AppTheme.colors.textSecondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }
            //ボタンを画面下部に配置するために説明文とボタンの間に大きめの余白を入れる
            //画面下に固定したい場合はPushScreenScaffoldを修正する必要あり
            item {
                Spacer(modifier = Modifier.height(500.dp))
            }
            item {
                Button(
                    onClick = {
                        //画面遷移先を後程追加
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ){
                    Text("呼び出し情報")
                }
            }
        }
    }
}
