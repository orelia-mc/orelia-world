<img src="https://orelia-mc.github.io/assets/logo_wide.jpg" />
<h1 align="center">Orelia World</h1>
<p align="center">Content Plugin of Orelia-MC</p>

## About

`orelia-world` は Minecraft RPG プラグイン群 **Orelia** のコンテンツプラグイン(Paper 1.21.x / Java 21)です。クエスト・NPC・ダイアログ・ストーリー・ダンジョン・カットシーン・イベントを扱います。

Orelia は 3 プラグイン構成です。

- [orelia-core](https://github.com/orelia-mc/orelia-core) — 戦闘・プレイヤー・ステータスの基盤(必須依存)
- **orelia-world**(本リポジトリ) — Quest, NPC, Dialogue, Story, Dungeon, CutScene, Event
- [orelia-extra](https://github.com/orelia-mc/orelia-extra) — 後発の MMORPG 系機能

起動には **orelia-core** が先にインストール・有効化されている必要があります(`plugin.yml` の `depend: [OreliaCore]`)。連携は `rpg.api`(Bukkit の `ServicesManager` で公開)経由のみで、orelia-core の内部モジュールクラスへ直接アクセスすることはありません。経済連携は他の Vault 対応プラグインと同様、Vault を直接利用します。

## Setup

```bash
./gradlew build
```

ビルドには `repo.papermc.io`(Paper API)と `jitpack.io`(orelia-core・Vault API を GitHub から解決)へのネットワークアクセスが必要です。

## Structure

- 設定ファイル — `quests.yml`, `npc.yml`, `dungeons.yml`, `dialogues.yml`, `story.yml`, `cutscenes.yml`, `events.yml`, `messages.yml`, `config.yml`。`/oladmin worldreload` で一括リロードできます。全ファイルが先頭の `config-version` で管理されており、新しいjarで起動すると新規追加されたキー(ネストした階層のキーも含む)は既存ファイルの正しい位置へ自動で追記されます(orelia-coreの `ConfigMigrator` をjitpack経由で共有)。新しいトップレベルセクション・キーを追加したら、そのファイルの `config-version` を1つ上げてください。`main` へのpush(=PRマージ)ごとに `.github/workflows/version-bump.yml` が `build.gradle.kts` の `version` を自動でPATCHインクリメントし、タグを打ちます。互換性が壊れる変更は `bump:minor`、大規模な改修は `bump:major` ラベルをPRに付けてからマージしてください。
- NPCは起動時に自動スポーンしません。`/oladmin npc spawnall` で `npc.yml` に設定済み・まだ出現していない全NPCをまとめて設置します(再実行しても重複しません)。職業指南役(job-change NPC)だけは対象外で、`/oladmin spawnnpc <npc-id>`(例: `/oladmin spawnnpc job_master`)で実行者の足元に個別に手動配置してください。
- ネザースターのプレイヤー情報メニュー(スキルタブ)から orelia-core の武器スキル画面(習得・レベルアップ・武器へのスキル装着)を開けます。
- クエスト — 前提クエスト(`quests.yml`の`prerequisite-quests`)は既存の仕組みで動作し、解放されると通知が届きます。リピート可能クエスト(`repeatable: true`)は`cooldown-hours`で再受注までの待機時間を設定できます(未設定/0は従来通り即再受注可)。`/ol quest list`には各クエストの目標ごとに進行バーが表示されます。
- 称号 — クエスト報酬(`reward.title`)で獲得した称号は`/ol title list`で確認、`/ol title equip <称号>`で装備できます(`/ol title unequip`で解除)。装備中の称号は`QuestApi#getEquippedTitle`経由で公開され、orelia-serverutilの`{title}`プレースホルダーからチャット/タブリストに表示できます。
- ダンジョン — `dungeons.yml`で敵の構成(`enemies:`、monsters.ymlのid×出現数)・任意のボス(`boss-id:`、bosses.ymlのid)・制限時間(`time-limit-seconds:`)・最大3件までの`arenas:`(物理的な入場座標、アリーナ数=同時進行できるパーティ数)を設定します。管理者は`/oladmin dungeonblock set <dungeon-id>`で見ているブロックをそのダンジョンの開放トリガーとして登録します(`remove`で解除、`list`で一覧)。プレイヤーはそのブロックを右クリックすると初回はダンジョンを「発見」(プレイヤーごとに永続、DB保存)、2回目以降のクリックか`/ol dungeon`(GUI)で難易度選択画面が開きます。難易度は5/10/15/20/30/40/50/60/70/80/90/100の固定ティアから自キャラのレベル以下のものだけ選択でき(選べるティアが無い場合は「通常」=スケーリング無しにフォールバック、8件目以降は`/ol dungeon`と同じくページ送りになります)、選んだ難易度は敵・ボスの`target_level`として反映されます(`/ol dungeon start <id> [difficulty]`でも直接指定可)。難易度を選ぶと即座にアリーナが確保され(空いているアリーナが無い場合は満員として挑戦を拒否)、「N秒後に挑戦します」という通知が1秒ごとにカウントダウン表示された後、実際のテレポート・敵スポーンが行われます(待機中に全員がログアウトした場合は予約を解放してキャンセル)。開始時、挑戦者がorelia-extraでパーティーを組んでいればそのメンバー(オンラインのみ)が同行者になり、組んでいなければソロで挑戦できます(OreliaExtra未導入時は常にソロ扱い)。開放済みかのチェックは挑戦パーティーのリーダーに対してのみ行われます。設定した敵とボスを全滅させれば制限時間内でクリア(報酬付与+クエストの`CLEAR_DUNGEON`目標が進行、ボス持ちダンジョンならメンバー1人あたり`config.yml`の`dungeon.relic-drop-min/max`で決まる個数のレリック(`rpg.api.RelicApi`、orelia-core側の厳選アクセサリー)もドロップ)、時間切れは報酬無しで強制退出、`/ol dungeon retire`でいつでも自主離脱できます(いずれも報酬無し、開放状態は失われず再挑戦可能)。クリア時はEXP・所持金の獲得量(`dungeons.yml`の`reward-exp`/`reward-money`)がチャットに表示され、レリックをドロップした場合は個数もメンバーごとに個別に表示されます。
