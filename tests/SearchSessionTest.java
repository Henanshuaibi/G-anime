import cn.g.anime.*;
import org.json.*;
import java.util.*;
public class SearchSessionTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Api.Page page(Source source,int pages,String id)throws Exception{
        Api.Page result=new Api.Page();result.count=pages;
        result.items.add(new JSONObject().put("_source",source.id).put("vod_id",id));return result;
    }
    public static void main(String[] args)throws Exception{
        Source a=Source.ALL[0],b=Source.ALL[1];SearchSession s=new SearchSession(Arrays.asList(a,b));
        List<SearchSession.Request> first=s.begin(false);
        check(first.size()==2&&first.get(0).page==1&&first.get(1).page==1,"start both sources on page one");
        check(s.busy()&&s.begin(false).isEmpty(),"avoid duplicate in-flight requests");
        s.success(first.get(0),page(a,3,"one"));s.failure(first.get(1));
        check(!s.busy()&&s.errors().contains(b.name),"track partial failures");
        List<SearchSession.Request> retry=s.begin(true);
        check(retry.size()==1&&retry.get(0).source==b&&retry.get(0).page==1,"retry only the failed source at the same page");
        s.success(retry.get(0),page(b,2,"one"));
        List<SearchSession.Request> second=s.begin(false);
        check(second.size()==2&&second.get(0).page==2&&second.get(1).page==2,"successful retry preserves independent page order");
        s.failure(second.get(0));s.success(second.get(1),page(b,2,"two"));
        List<SearchSession.Request> third=s.begin(false);
        check(third.size()==1&&third.get(0).source==a&&third.get(0).page==2,"failed page cannot be skipped by load more");
        s.cancelPending();List<SearchSession.Request> replacement=s.begin(false);
        s.success(third.get(0),page(a,3,"stale"));
        check(s.busy()&&s.items().size()==3,"ignore a late response from a canceled request");
        s.success(replacement.get(0),page(a,3,"two"));
        List<SearchSession.Request> last=s.begin(false);
        check(last.size()==1&&last.get(0).page==3,"do not query exhausted providers");
        s.success(last.get(0),page(a,3,"two"));
        check(!s.hasMore()&&!s.busy()&&s.items().size()==4,"deduplicate repeated identities and finish pagination");
        check(s.errors().isEmpty()&&s.begin(true).isEmpty(),"clear recovered failures");
        SearchSession empty=new SearchSession(Collections.singletonList(a));SearchSession.Request r=empty.begin(false).get(0);
        empty.success(r,new Api.Page());check(!empty.hasMore(),"zero-page empty responses terminate");
        System.out.println("Search pagination checks passed: "+checks);
    }
}
