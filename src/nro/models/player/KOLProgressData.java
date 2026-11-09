package nro.models.player;

/**
 *
 * @author Việt
 */
public class KOLProgressData {

    public int kolQuestStage;
    public int kolVIPQuestStage;
    public int destronGas70CompletionCount;
    public int martialArtsTournamentWins;
    public int dailySuperHardQuestCompletionCount;
    public int bossBabyDefeatParticipationCount;
    public long monsterKillCountAutoTrain;
    public long kolBoMong, kolMocNhan, kolBdkb, kolNinjaTim;
    public boolean kolVip;
    /** Đợt KOL lúc lưu; khác đợt đang chạy thì tiến độ bắt đầu lại từ đầu. */
    public String season;

    public KOLProgressData() {
        this.kolQuestStage = 1;
        this.kolVIPQuestStage = 1;
        this.destronGas70CompletionCount = 0;
        this.martialArtsTournamentWins = 0;
        this.dailySuperHardQuestCompletionCount = 0;
        this.bossBabyDefeatParticipationCount = 0;
        this.monsterKillCountAutoTrain = 0;
    }
}
