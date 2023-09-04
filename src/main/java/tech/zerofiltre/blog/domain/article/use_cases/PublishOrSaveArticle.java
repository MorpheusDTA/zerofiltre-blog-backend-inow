package tech.zerofiltre.blog.domain.article.use_cases;

import tech.zerofiltre.blog.domain.Domains;
import tech.zerofiltre.blog.domain.article.ArticleProvider;
import tech.zerofiltre.blog.domain.article.TagProvider;
import tech.zerofiltre.blog.domain.article.model.Article;
import tech.zerofiltre.blog.domain.article.model.Status;
import tech.zerofiltre.blog.domain.article.model.Tag;
import tech.zerofiltre.blog.domain.error.ForbiddenActionException;
import tech.zerofiltre.blog.domain.user.UserNotificationProvider;
import tech.zerofiltre.blog.domain.user.model.Action;
import tech.zerofiltre.blog.domain.user.model.User;
import tech.zerofiltre.blog.domain.user.model.UserActionEvent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

public class PublishOrSaveArticle {

    private final ArticleProvider articleProvider;
    private final TagProvider tagProvider;
    private final UserNotificationProvider notificationProvider;

    public PublishOrSaveArticle(ArticleProvider articleProvider, TagProvider tagProvider, UserNotificationProvider notificationProvider) {
        this.articleProvider = articleProvider;
        this.tagProvider = tagProvider;
        this.notificationProvider = notificationProvider;
    }


    public Article execute(User currentEditor, long articleId, String title, String thumbnail, String summary, String content, List<Tag> tags, Status status, String appUrl) throws PublishOrSaveArticleException, ForbiddenActionException {

        LocalDateTime now = LocalDateTime.now();

        Article existingArticle = articleProvider.articleOfId(articleId)
                .orElseThrow(() -> new PublishOrSaveArticleException("We can not publish/save an unknown article. Could not find an article with id: " + articleId, articleId));

        User author = existingArticle.getAuthor();
        if (!isAuthor(currentEditor, author) && !currentEditor.isAdmin())
            throw new ForbiddenActionException("You are not allowed to edit this article", Domains.ARTICLE.name());

        checkTags(tags);
        existingArticle.setTags(tags);
        existingArticle.setTitle(title);
        existingArticle.setThumbnail(thumbnail);
        existingArticle.setContent(content);
        existingArticle.setSummary(summary);
        existingArticle.setLastSavedAt(now);

        if (!isAlreadyPublished(existingArticle) && isTryingToPublish(status) && !currentEditor.isAdmin()) {
            existingArticle.setStatus(Status.IN_REVIEW);

            UserActionEvent  userActionEvent = new  UserActionEvent(appUrl, Locale.forLanguageTag(author.getLanguage()), author, "", existingArticle, Action.ARTICLE_SUBMITTED);
            notificationProvider.notify(userActionEvent);
        }

        if (!isAlreadyPublished(existingArticle) && (!isTryingToPublish(status) || currentEditor.isAdmin()))
            existingArticle.setStatus(status);


        //If article has been published few lines upper, check if the published date is set, otherwise do it.
        if (isAlreadyPublished(existingArticle)) {
            if (existingArticle.getPublishedAt() == null)
                existingArticle.setPublishedAt(now);
            existingArticle.setLastPublishedAt(now);
        }

        return articleProvider.save(existingArticle);
    }


    private boolean isAlreadyPublished(Article existingArticle) {
        return existingArticle.getStatus().equals(Status.PUBLISHED);
    }

    private boolean isTryingToPublish(Status status) {
        return status.equals(Status.PUBLISHED) || status.equals(Status.IN_REVIEW);
    }

    private boolean isAuthor(User currentEditor, User author) {
        return currentEditor.getEmail().equals(author.getEmail());
    }

    private void checkTags(List<Tag> tags) throws PublishOrSaveArticleException {
        for (Tag tag : tags) {
            if (tagProvider.tagOfId(tag.getId()).isEmpty())
                throw new PublishOrSaveArticleException("We can not publish the article. Could not find the related tag with id: " + tag.getId(), tag.getId());
        }
    }
}
