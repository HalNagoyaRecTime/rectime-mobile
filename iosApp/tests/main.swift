import Foundation

func checkScenePlayback() {
    var scene = SplashScenePlayback()
    scene.setActive(true, at: 0)
    precondition(scene.elapsed(since: 0, at: 0.7) == 0.7)
    scene.setActive(false, at: 0.7)
    precondition(!scene.isActive)
    // 別のシーンが動作していても、このシーンの時刻は進まない。
    precondition(scene.elapsed(since: 0, at: 10) == 0.7)
    scene.setActive(false, at: 20)
    scene.setActive(true, at: 30)
    precondition(abs(scene.elapsed(since: 0, at: 30.075) - 0.775) < 0.0001)
    scene.setActive(false, at: 31)
    scene.setActive(true, at: 41)
    precondition(abs(scene.elapsed(since: 0, at: 41) - 1.7) < 0.0001)
    scene.finish()
    scene.setActive(true, at: 50)
    precondition(scene.finished && !scene.isActive)
}

checkScenePlayback()
print("SplashScenePlayback: pause, resume and permanent finish passed")
