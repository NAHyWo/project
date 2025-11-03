package io.project.na;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Array;

public class Main extends ApplicationAdapter {
    // 렌더링 관련 객체
    SpriteBatch batch;
    OrthographicCamera camera;
    BitmapFont font;

    // 텍스처 리소스
    Texture playerTex;
    Texture[] platformTex;
    Texture coinTex;
    Texture[] bgTex;
    Texture[] monsterTex;
    Texture pixel;

    Texture winTex;
    Texture loseTex;
    Texture pauseTex;
    Texture heartTex;
    Texture windTex;

    // 사운드 관련
    Sound coinSound;
    Sound monsterSound;
    Sound winSound;
    Sound loseSound;
    Music bgMusic;

    // 플레이어 정보
    Rectangle player;
    float velX = 0;
    float velY = 0;
    float moveSpeed = 400;
    final float jumpPower = 550;
    int maxHealth = 3;
    int health = 3;

    // 몬스터에 닿았을 때 넉백 관련 변수
    float knockbackTime = 0f;
    final float knockbackDuration = 0.15f;

    boolean playerFacingRight = true;

    // 플랫폼 및 스폰 관련
    Array<Rectangle> platforms;
    float nextPlatformY;
    boolean leftSide = true;

    Array<Rectangle> coins;
    int score = 0;

    // 히트 블링크 효과용 변수
    float blinkTime = 0f;
    final float blinkDuration = 0.8f;
    boolean isBlinking = false;

    // 바람 텍스처 이동 연출용 오프셋
    float windOffset = 0f;

    // 몬스터 클래스
    class Monster {
        Rectangle rect;
        float speed;
        boolean movingRight;
        boolean toRemove = false;
        int texIndex;

        Monster(float x, float y, int level) {
            rect = new Rectangle(x, y, 100, 100);
            // 레벨이 높을수록 속도 범위 증가
            speed = 100 + MathUtils.random(0, 50);
            movingRight = MathUtils.randomBoolean();
            texIndex = MathUtils.clamp(level - 1, 0, monsterTex.length - 1);
        }

        void update(float dt) {
            // 좌우 이동
            if (movingRight) rect.x += speed * dt;
            else rect.x -= speed * dt;

            // 화면 벽에 닿으면 방향 반전
            if (rect.x < 0) movingRight = true;
            if (rect.x + rect.width > 720) movingRight = false;
        }
    }

    Array<Monster> monsters;

    int currentLevel = 1;
    final int maxLevel = 3;

    // 레벨별 물리/목표 설정 클래스
    static class LevelPhysics {
        float gravity, wind, monsterSpawnProb;
        int coinTarget;

        LevelPhysics(float g, float w, int c, float mProb) {
            gravity = g;
            wind = w;
            coinTarget = c;
            monsterSpawnProb = mProb;
        }
    }

    LevelPhysics[] levelPhysics = new LevelPhysics[3];

    enum GameState { PLAYING, CLEAR, GAMEOVER, PAUSE, TRANSITION, TRANSITION_IN, CLEAR_FADE }
    GameState state = GameState.PLAYING;

    float maxCameraY = 640;
    float fadeAlpha = 0f;
    float fadeSpeed = 1.5f;
    float cameraLockY = 640;
    float windDirection = 1f;
    float windTimer = 0f;
    float windInterval = 1.5f;

    // 리소스 및 레벨 초기화
    @Override
    public void create() {
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 720, 1280);
        font = new BitmapFont();
        font.getData().setScale(2.5f);

        // 플랫폼 텍스처
        platformTex = new Texture[3];
        platformTex[0] = new Texture("platform1.png");
        platformTex[1] = new Texture("platform2.png");
        platformTex[2] = new Texture("platform3.png");

        // 동전
        coinTex = new Texture("coin.png");

        // 배경
        bgTex = new Texture[3];
        bgTex[0] = new Texture("background1.png");
        bgTex[1] = new Texture("background2.png");
        bgTex[2] = new Texture("background3.png");

        // 몬스터 텍스처
        monsterTex = new Texture[3];
        monsterTex[0] = new Texture("monster1.png");
        monsterTex[1] = new Texture("monster2.png");
        monsterTex[2] = new Texture("monster3.png");

        // 기타 UI
        playerTex = new Texture("player.png");
        heartTex = new Texture("heart.png");
        windTex = new Texture("wind.png");
        pixel = new Texture("pixel.png");
        winTex = new Texture("win.png");
        loseTex = new Texture("lose.png");
        pauseTex = new Texture("pause.png");

        // 플레이어 기본 위치
        player = new Rectangle(360 - 32, 100, 64, 64);

        // 오브젝트 리스트 초기화
        platforms = new Array<>();
        coins = new Array<>();
        monsters = new Array<>();

        // 레벨별 물리 값 설정
        levelPhysics[0] = new LevelPhysics(-800, 0, 3, 0.3f);
        levelPhysics[1] = new LevelPhysics(-800, 50, 5, 0.5f);
        levelPhysics[2] = new LevelPhysics(-500, 0, 7, 0.7f);

        // 사운드 로드
        coinSound = Gdx.audio.newSound(Gdx.files.internal("coin.wav"));
        monsterSound = Gdx.audio.newSound(Gdx.files.internal("monster.wav"));
        winSound = Gdx.audio.newSound(Gdx.files.internal("win.wav"));
        loseSound = Gdx.audio.newSound(Gdx.files.internal("lose.wav"));

        // 1레벨 배경음 시작
        playBackgroundMusic(currentLevel);

        resetLevel();
    }

    // 레벨별 배경음 재생
    private void playBackgroundMusic(int level) {
        if (bgMusic != null) {
            bgMusic.stop();
            bgMusic.dispose();
        }
        String file = "background" + level + ".wav";
        bgMusic = Gdx.audio.newMusic(Gdx.files.internal(file));
        bgMusic.setLooping(true);
        bgMusic.setVolume(0.5f);
        bgMusic.play();
    }

    // 게임 로직 업데이트
    private void updateGame(float dt) {
        // ESC 일시정지 / 해제
        if(Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if(state == GameState.PLAYING) state = GameState.PAUSE;
            else if(state == GameState.PAUSE) state = GameState.PLAYING;
        }

        if(state == GameState.PAUSE) return;

        // 레벨 전환 페이드 처리
        if(state == GameState.TRANSITION || state == GameState.CLEAR_FADE) {
            camera.position.y = cameraLockY;
            camera.update();
            fadeAlpha += fadeSpeed * dt;
            if(fadeAlpha >= 1f) {
                fadeAlpha = 1f;
                if(state == GameState.TRANSITION) {
                    nextLevelSetup();
                    state = GameState.TRANSITION_IN;
                } else state = GameState.CLEAR;
            }
            return;
        }
        else if(state == GameState.TRANSITION_IN) {
            camera.position.y = cameraLockY;
            camera.update();
            fadeAlpha -= fadeSpeed * dt;
            if(fadeAlpha <= 0f) {
                fadeAlpha = 0f;
                state = GameState.PLAYING;
            }
            return;
        }

        // 게임오버 SPACE로 재시작
        if(state == GameState.GAMEOVER) {
            if(Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) restartGame();
            return;
        }
        if(state != GameState.PLAYING) return;

        LevelPhysics lv = levelPhysics[MathUtils.clamp(currentLevel - 1, 0, levelPhysics.length - 1)];

        // 바람 방향 랜덤 전환
        if (lv.wind != 0) {
            windTimer += dt;
            if (windTimer >= windInterval) {
                windTimer = 0;
                windDirection = MathUtils.randomBoolean() ? 1 : -1;
            }
            windOffset += lv.wind * windDirection * dt;
        }

        // 넉백 유지 시간 처리
        if(knockbackTime > 0) {
            player.x += velX * dt;
            knockbackTime -= dt;
            if(knockbackTime <= 0) velX = 0;
        } else {
            // 플레이어 좌우 이동
            if(Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
                player.x -= moveSpeed * dt;
                playerFacingRight = false;
            }
            if(Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
                player.x += moveSpeed * dt;
                playerFacingRight = true;
            }
            // 바람 영향
            player.x += lv.wind * windDirection * dt;
        }

        // 중력
        velY += lv.gravity * dt;
        player.y += velY * dt;

        // 플레이어 위로 계속 플랫폼 생성
        while(nextPlatformY < player.y + 2000) {
            float xBase = leftSide ? 100 : 720 - 300;
            float adjustedX = xBase + MathUtils.random(-50, 50);
            platforms.add(new Rectangle(adjustedX, nextPlatformY, 200, 50));
            if(MathUtils.randomBoolean(0.5f)) coins.add(new Rectangle(adjustedX + 60, nextPlatformY + 30, 64, 64));
            if(MathUtils.randomBoolean(lv.monsterSpawnProb)) monsters.add(new Monster(adjustedX + 50, nextPlatformY + 40, currentLevel));
            nextPlatformY += 170;
            leftSide = !leftSide;
        }

        // 점프 판정 (platform 위면 점프력 부여)
        if(velY <= 0) {
            for(Rectangle p : platforms) {
                if(player.overlaps(p) && player.y > p.y + p.height/2) {
                    player.y = p.y + p.height;
                    velY = jumpPower;
                }
            }
        }

        // 동전 획득
        for(int i = coins.size - 1; i >= 0; i--) {
            if(player.overlaps(coins.get(i))) {
                coins.removeIndex(i);
                score++;
                coinSound.play(0.7f);
            }
        }

        // 몬스터 충돌
        for(Monster m : monsters) {
            m.update(dt);
            if(player.overlaps(m.rect) && !m.toRemove) {
                health--;
                isBlinking = true;
                blinkTime = 0f;

                // 충돌 방향 분석
                float overlapX = (player.x + player.width/2) - (m.rect.x + m.rect.width/2);
                float overlapY = (player.y + player.height/2) - (m.rect.y + m.rect.height/2);

                if(Math.abs(overlapY) > Math.abs(overlapX)) {
                    velY = jumpPower * 0.8f;
                } else {
                    velX = (overlapX > 0 ? 1 : -1) * 300;
                    knockbackTime = knockbackDuration;
                }

                m.toRemove = true;
                monsterSound.play(0.7f);

                // 체력 0 게임 종료
                if(health <= 0) {
                    state = GameState.GAMEOVER;
                    if(bgMusic != null) bgMusic.stop();
                    loseSound.play(0.7f);
                }
            }
        }

        // 제거된 몬스터 정리
        for(int i = monsters.size - 1; i >= 0; i--) {
            if(monsters.get(i).toRemove) monsters.removeIndex(i);
        }

        // 카메라 아래로 사라진 오브젝트 정리
        float removeY = camera.position.y - 1200;
        for(int i = platforms.size - 1; i >= 0; i--) {
            if(platforms.get(i).y + platforms.get(i).height < removeY) platforms.removeIndex(i);
        }
        for(int i = coins.size - 1; i >= 0; i--) {
            if(coins.get(i).y + coins.get(i).height < removeY) coins.removeIndex(i);
        }
        for(int i = monsters.size - 1; i >= 0; i--) {
            if(monsters.get(i).rect.y + monsters.get(i).rect.height < removeY) monsters.removeIndex(i);
        }

        // 카메라 상승
        if(player.y + 300 > maxCameraY) maxCameraY = player.y + 300;
        camera.position.y = Math.max(player.y + 300, 640);
        camera.update();

        // 화면 아래로 벗어나면 사망
        if(player.y + player.height < camera.position.y - 700) {
            state = GameState.GAMEOVER;
            if(bgMusic != null) bgMusic.stop();
            loseSound.play(0.7f);
        }

        // 레벨 목표 달성
        if(score >= lv.coinTarget) {
            fadeAlpha = 0f;
            cameraLockY = camera.position.y;
            if(currentLevel >= maxLevel) {
                state = GameState.CLEAR_FADE;
                if(bgMusic != null) bgMusic.stop();
                winSound.play(0.7f);
            } else state = GameState.TRANSITION;
        }

        // 히트 블링크 종료 감지
        if(isBlinking) {
            blinkTime += Gdx.graphics.getDeltaTime();
            if(blinkTime >= blinkDuration) {
                isBlinking = false;
                blinkTime = 0f;
            }
        }
    }

    // 다음 레벨 세팅
    private void nextLevelSetup() {
        cameraLockY = 640f;
        currentLevel++;
        if(currentLevel > maxLevel) currentLevel = maxLevel;

        // 레벨 클리어마다 체력 1 회복
        score = 0;
        health = Math.min(health + 1, maxHealth);

        camera.position.y = 640;
        camera.update();
        maxCameraY = 640;

        leftSide = true;
        windTimer = 0;
        windDirection = 1;

        playBackgroundMusic(currentLevel);
        resetLevel();
    }

    // 레벨 초기화
    private void resetLevel() {
        LevelPhysics lv = levelPhysics[MathUtils.clamp(currentLevel - 1, 0, levelPhysics.length - 1)];

        cameraLockY = 640f;
        player.x = 360 - 32;
        player.y = 100;
        velX = 0;
        velY = 0;
        maxCameraY = player.y + 300;

        platforms.clear();
        coins.clear();
        monsters.clear();

        leftSide = true;
        // 시작 플랫폼
        platforms.add(new Rectangle(player.x - 70, player.y - 50, 200, 50));
        nextPlatformY = player.y + 100;

        // 위쪽으로 플랫폼 생성
        while(nextPlatformY < player.y + 2000) {
            float xBase = leftSide ? 100 : 720 - 300;
            float x = xBase + MathUtils.random(-50, 50);
            platforms.add(new Rectangle(x, nextPlatformY, 200, 50));
            if(MathUtils.randomBoolean(0.5f)) coins.add(new Rectangle(x + 60, nextPlatformY + 30, 64, 64));
            if(MathUtils.randomBoolean(lv.monsterSpawnProb)) monsters.add(new Monster(x + 50, nextPlatformY + 40, currentLevel));
            nextPlatformY += 170;
            leftSide = !leftSide;
        }
    }

    /** 전체 게임 재시작 */
    private void restartGame() {
        if(bgMusic != null) {
            bgMusic.stop();
            bgMusic.dispose();
            bgMusic = null;
        }

        currentLevel = 1;
        score = 0;
        health = maxHealth;
        state = GameState.PLAYING;
        fadeAlpha = 0f;
        maxCameraY = 640;
        camera.position.y = 640;
        camera.update();

        windTimer = 0;
        windDirection = 1;

        playBackgroundMusic(currentLevel);
        resetLevel();
    }

    // 렌더링
    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        updateGame(dt);

        Gdx.gl.glClearColor(0,0,0.1f,1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // 게임 진행 중 또는 페이드 중 배경 및 오브젝트 렌더링
        if(state == GameState.PLAYING || state == GameState.PAUSE || state == GameState.TRANSITION || state == GameState.TRANSITION_IN || state == GameState.CLEAR_FADE) {
            Texture bg = bgTex[Math.min(currentLevel - 1, maxLevel - 1)];
            batch.draw(bg, camera.position.x - 360, camera.position.y - 640, 720, 1280);

            // 바람 텍스처 반복 그리기
            LevelPhysics lv = levelPhysics[MathUtils.clamp(currentLevel - 1, 0, levelPhysics.length - 1)];
            if(lv.wind != 0) {
                float wWidth = windTex.getWidth();
                float wHeight = windTex.getHeight();
                for(float x = -wWidth + windOffset; x < 720; x += wWidth) {
                    batch.draw(windTex, camera.position.x - 360 + x, camera.position.y + 640 - wHeight, wWidth, wHeight);
                }
            }

            Texture pTex = platformTex[Math.min(currentLevel - 1, platformTex.length - 1)];
            for(Rectangle p : platforms) batch.draw(pTex, p.x, p.y, p.width, p.height);
            for(Rectangle c : coins) batch.draw(coinTex, c.x,c.y,c.width,c.height);

            // 몬스터 좌우 반전 렌더링
            for(Monster m : monsters) {
                float drawX = m.movingRight ? m.rect.x : m.rect.x + m.rect.width;
                float scaleX = m.movingRight ? 1f : -1f;
                batch.draw(monsterTex[m.texIndex], drawX, m.rect.y, m.rect.width * scaleX, m.rect.height);
            }

            // 플레이어 반전 렌더링
            float pDrawX = playerFacingRight ? player.x : player.x + player.width;
            float pScaleX = playerFacingRight ? 1f : -1f;
            batch.draw(playerTex, pDrawX, player.y, player.width * pScaleX, player.height);

            // 좌상단 동전 카운트 UI
            float coinSize = 40f;
            float padding = 10f;
            float coinX = camera.position.x - 360 + padding;
            float coinY = camera.position.y + 640 - coinSize - padding;
            batch.draw(coinTex, coinX, coinY, coinSize, coinSize);
            String coinText = score + " / " + lv.coinTarget;
            font.draw(batch, coinText, coinX + coinSize + 5, coinY + coinSize - 5);
        }

        // 페이드 렌더링
        if(state == GameState.TRANSITION || state == GameState.TRANSITION_IN || state == GameState.CLEAR_FADE) {
            batch.setColor(0,0,0,fadeAlpha);
            batch.draw(pixel, camera.position.x - 360, camera.position.y - 640, 720,1280);
            batch.setColor(1,1,1,1);
        }

        // 체력 표시
        float heartWidth = 40;
        float heartHeight = 40;
        float heartPadding = 10;
        float startX = camera.position.x + 360 - heartWidth - 10;
        float startY = camera.position.y + 640 - heartHeight - 10;
        for(int i=0; i<health; i++) {
            if(isBlinking) {
                float alpha = 0.5f + 0.5f * MathUtils.sin(blinkTime * 20f);
                batch.setColor(1,1,1,alpha);
            } else batch.setColor(1,1,1,1);
            batch.draw(heartTex, startX - i*(heartWidth+heartPadding), startY, heartWidth, heartHeight);
            batch.setColor(1,1,1,1);
        }

        // 결과 화면
        if(state == GameState.GAMEOVER) {
            batch.draw(loseTex, camera.position.x - 360, camera.position.y - 640, 720,1280);
        }
        if(state == GameState.CLEAR) {
            batch.draw(winTex, camera.position.x - 360, camera.position.y - 640, 720,1280);
        }
        if(state == GameState.PAUSE) {
            batch.draw(pauseTex, camera.position.x - 360, camera.position.y - 640, 720,1280);
        }

        batch.end();
    }

    // 리소스 정리
    @Override
    public void dispose() {
        batch.dispose();
        playerTex.dispose();
        for(Texture t : platformTex) t.dispose();
        coinTex.dispose();
        pixel.dispose();
        for(Texture t : bgTex) t.dispose();
        for(Texture t : monsterTex) t.dispose();
        windTex.dispose();
        font.dispose();
        winTex.dispose();
        loseTex.dispose();
        pauseTex.dispose();
        heartTex.dispose();

        // 사운드 해제
        coinSound.dispose();
        monsterSound.dispose();
        winSound.dispose();
        loseSound.dispose();
        if(bgMusic != null) {
            bgMusic.stop();
            bgMusic.dispose();
        }
    }
}
