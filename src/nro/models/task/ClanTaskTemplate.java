package nro.models.task;

/**
 *
 * @author Việt
 * 
 */

public class ClanTaskTemplate {

    public int id;
    public String name;
    public int[][] count;

    public ClanTaskTemplate() {
        this.count = new int[5][2];
    }

}
