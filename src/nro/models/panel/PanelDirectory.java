package nro.models.panel;

import java.util.*;

/** Read-only directory pagination. All search values are bound parameters. */
final class PanelDirectory {
    private PanelDirectory() {}
    static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception e) { return fallback; }
    }
    static int size(Map<String,String> params) {
        int n=integer(params.get("size"),25);
        return n==50||n==100?n:25;
    }
    static long numeric(Object value) {
        if(value instanceof Number)return ((Number)value).longValue();
        try{return Long.parseLong(String.valueOf(value));}catch(Exception e){return 0;}
    }
    static final class Page {
        final String sql;
        final List<Object> args;
        final int page,size;
        final long total,totalPages;
        Page(String sql,List<Object> args,int requested,int size,long total){
            this.size=size;this.total=Math.max(0,total);
            this.totalPages=Math.max(1,(this.total+size-1)/size);
            this.page=(int)Math.max(1,Math.min(requested,this.totalPages));
            this.sql=sql;this.args=new ArrayList<>(args);
            if(sql!=null){this.args.add(size);this.args.add((long)(this.page-1)*size);}
        }
        Map<String,Object> result(List<?> list){
            Map<String,Object> out=new LinkedHashMap<>();
            out.put("rows",list);out.put("page",page);out.put("size",size);
            out.put("total",total);out.put("totalPages",totalPages);
            return out;
        }
    }
    static final class Query {
        final String countSql,dataSql;
        final List<Object> countArgs;
        final int requested,size;
        Query(String where,List<Object> args,String order,int requested,int size){
            String from=" from player p left join account a on a.id=p.account_id where "+where;
            countSql="select count(*) total"+from;
            dataSql="select p.id,p.name,p.gender,p.head,p.items_body,a.username,a.ban,a.is_admin,p.data_point,p.data_inventory,p.data_location"+from+" order by "+order+" limit ? offset ?";
            countArgs=args;this.requested=requested;this.size=size;
        }
        Page page(long total){return new Page(dataSql,countArgs,requested,size,total);}
    }
    private static void ids(StringBuilder where,List<Object> args,Collection<Long> ids,boolean include){
        if(ids.isEmpty()){if(include)where.append(" and 1=0");return;}
        List<Long> clean=new ArrayList<>(new TreeSet<>(ids));
        where.append(include?" and p.id in (":" and p.id not in (");
        where.append(String.join(",",Collections.nCopies(clean.size(),"?"))).append(")");
        args.addAll(clean);
    }
    static Query query(Map<String,String> params,Collection<Long> online,Collection<Long> bots){
        StringBuilder where=new StringBuilder("1=1");List<Object> args=new ArrayList<>();
        String keyword=params.getOrDefault("q","").trim();
        if(keyword.length()>100)keyword=keyword.substring(0,100);
        if(!keyword.isEmpty()){
            String pattern="%"+keyword.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
            where.append(" and (p.name like ? escape '!' or a.username like ? escape '!' or p.id = ?)");
            args.add(pattern);args.add(pattern);
            long id=-1;try{id=Long.parseLong(keyword);}catch(NumberFormatException ignored){}
            args.add(id);
        }
        String state=params.getOrDefault("state","");
        if(state.equals("online"))ids(where,args,online,true);
        if(state.equals("offline"))ids(where,args,online,false);
        if(state.equals("banned"))where.append(" and a.ban = 1");
        String kind=params.getOrDefault("kind","");
        if(kind.equals("bot"))ids(where,args,bots,true);
        if(kind.equals("human"))ids(where,args,bots,false);
        int gender=integer(params.get("gender"),-1);
        if(gender>=0&&gender<=2){where.append(" and p.gender = ?");args.add(gender);}
        String order=switch(params.getOrDefault("sort","id_desc")){
            case "id_asc"->"p.id asc";
            case "name_asc"->"p.name asc, p.id desc";
            case "name_desc"->"p.name desc, p.id desc";
            default->"p.id desc";
        };
        return new Query(where.toString(),args,order,Math.max(1,integer(params.get("page"),1)),size(params));
    }
    static Map<String,Object> slice(List<Map<String,Object>> input,Map<String,String> params){
        List<Map<String,Object>> list=new ArrayList<>(input);
        String sort=params.getOrDefault("sort","id_asc");
        Comparator<Map<String,Object>> cmp;
        if(sort.equals("name_asc"))cmp=Comparator.comparing(x->String.valueOf(x.get("name")),String.CASE_INSENSITIVE_ORDER);
        else cmp=Comparator.comparingLong(x->numeric(x.get("id")));
        if(sort.equals("id_desc"))cmp=cmp.reversed();
        list.sort(cmp.thenComparingLong(x->numeric(x.get("id"))));
        Page p=new Page(null,List.of(),Math.max(1,integer(params.get("page"),1)),size(params),list.size());
        int from=(p.page-1)*p.size;
        return p.result(new ArrayList<>(list.subList(from,Math.min(from+p.size,list.size()))));
    }
}
