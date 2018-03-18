package jp._5000164.backlog_bot.interfaces

import java.util.Date

import com.nulabinc.backlog4j.api.option.QueryParams
import com.nulabinc.backlog4j.conf.{BacklogConfigure, BacklogJpConfigure}
import com.nulabinc.backlog4j.internal.json.activities._
import com.nulabinc.backlog4j.{Activity, BacklogClient, BacklogClientFactory, PullRequestComment}
import jp._5000164.backlog_bot.domain.{Message, MessageBundle}
import jp._5000164.backlog_bot.infractructure.Settings

import scala.collection.JavaConverters._

class Backlog {
  val spaceId = sys.env("BACKLOG_SPACE_ID")
  val apiKey = sys.env("BACKLOG_API_KEY")
  val configure: BacklogConfigure = new BacklogJpConfigure(spaceId).apiKey(apiKey)
  val client: BacklogClient = new BacklogClientFactory(configure).newClient()

  def fetchMessages(lastExecutedAt: Date, mapping: Map[Settings.settings.ProjectKey, Settings.settings.PostChannel]): List[MessageBundle] = {
    mapping.toList.map {
      case (projectKey, postChannel) =>
        val project = client.getProject(projectKey)
        val activities = client.getProjectActivities(project.getId)
        val messages = activities.asScala.filter(_.getCreated after lastExecutedAt).map {
          case activity if activity.getType == Activity.Type.IssueCreated =>
            val content = activity.getContent.asInstanceOf[IssueCreatedContent]
            val issue = client.getIssue(content.getId)
            Some(Message.build(spaceId, projectKey, activity, content, issue))
          case activity if activity.getType == Activity.Type.IssueUpdated =>
            val content = activity.getContent.asInstanceOf[IssueUpdatedContent]
            val comment = client.getIssueComment(content.getId, content.getComment.getId)
            Some(Message.build(spaceId, projectKey, activity, content, comment))
          case activity if activity.getType == Activity.Type.IssueCommented =>
            val content = activity.getContent.asInstanceOf[IssueCommentedContent]
            val comment = client.getIssueComment(content.getId, content.getComment.getId)
            Some(Message.build(spaceId, projectKey, activity, content, comment))
          case activity if activity.getType == Activity.Type.WikiCreated =>
            val content = activity.getContent.asInstanceOf[WikiCreatedContent]
            Some(Message.build(spaceId, projectKey, activity, content))
          case activity if activity.getType == Activity.Type.WikiUpdated =>
            val content = activity.getContent.asInstanceOf[WikiUpdatedContent]
            Some(Message.build(spaceId, projectKey, activity, content))
          case activity if activity.getType == Activity.Type.GitPushed =>
            // 実装の活動としてはプルリクエストとして観測するため push されたイベントには反応しないようにする
            None
          case activity if activity.getType == Activity.Type.PullRequestAdded =>
            val content = activity.getContent.asInstanceOf[PullRequestContent]
            val pullRequest = client.getPullRequest(project.getId, content.getRepository.getId, content.getNumber)
            Some(Message.build(spaceId, projectKey, activity, content, pullRequest))
          case activity if activity.getType == Activity.Type.PullRequestUpdated =>
            val content = activity.getContent.asInstanceOf[PullRequestContent]
            val comment = client.getPullRequestComments(project.getId, content.getRepository.getId, content.getNumber, (new QueryParams).minId(content.getComment.getId - 1).maxId(content.getComment.getId + 1).count(1)).toArray.head.asInstanceOf[PullRequestComment]
            Some(Message.build(spaceId, projectKey, activity, content, comment))
          case _ =>
            Some(Message(authorName = None, pretext = None, title = None, link = None, text = Some("対応していない操作です")))
        }.toList.flatten.reverse
        MessageBundle(postChannel, messages)
    }
  }
}