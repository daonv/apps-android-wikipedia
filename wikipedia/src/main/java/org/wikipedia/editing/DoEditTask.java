package org.wikipedia.editing;

import android.content.*;
import android.util.*;
import org.json.*;
import org.mediawiki.api.json.*;
import org.wikipedia.*;
import org.wikipedia.concurrency.*;

import java.util.concurrent.Executor;

public class DoEditTask extends ApiTask<EditingResult> {
    private final PageTitle title;
    private final String sectionWikitext;
    private final int sectionID;
    private final String editToken;
    private final WikipediaApp app;

    public DoEditTask(Context context, PageTitle title, String sectionWikitext, int sectionID, String editToken) {
        super(
                ExecutorService.getSingleton().getExecutor(DoEditTask.class, 1),
                ((WikipediaApp)context.getApplicationContext()).getAPIForSite(title.getSite())
        );
        this.title = title;
        this.sectionWikitext = sectionWikitext;
        this.sectionID = sectionID;
        this.editToken = editToken;
        this.app = (WikipediaApp)context.getApplicationContext();
    }

    @Override
    public RequestBuilder buildRequest(Api api) {
        return api.action("edit")
                .param("title", title.getPrefixedText())
                .param("section", String.valueOf(sectionID))
                .param("text", sectionWikitext)
                .param("token", editToken);
    }

    @Override
    protected ApiResult makeRequest(RequestBuilder builder) {
        return builder.post(); // Editing requires POST requests
    }

    @Override
    public EditingResult processResult(ApiResult result) throws Throwable {
        JSONObject resultJSON = result.asObject();

        if (WikipediaApp.isWikipediaZeroDevmodeOn()) {
            // TODO: ??? not sure if this is a better place for this or down below,
            // but the block seems to be ready for revisions, so clean up then?
            Utils.processHeadersForZero(app, result);
        }
        Log.d("Wikipedia", resultJSON.toString(4));
        if (resultJSON.has("error")) {
            JSONObject errorJSON = resultJSON.optJSONObject("error");
            throw new EditingException(errorJSON.optString("code"), errorJSON.optString("info"));
        }
        JSONObject edit = resultJSON.optJSONObject("edit");
        String status = edit.optString("result");
        if (status.equals("Success")) {
            return new SuccessEditResult();
        } else if (status.equals("Failure")) {
            if (edit.has("captcha")) {
                return new CaptchaResult(
                        edit.optJSONObject("captcha").optString("id")
                );
            }
            if (edit.has("code") && edit.optString("code").startsWith("abusefilter-")) {
                return new AbuseFilterEditResult(edit);
            }
        }
        // Handle other type of return codes here
        throw new RuntimeException("Failure happens");
    }
}
