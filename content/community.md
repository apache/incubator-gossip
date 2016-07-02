---
layout: page
title: Community
description: Project Community Page
group: nav-right
---
<!--
{% comment %}
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to you under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
{% endcomment %}
-->

{% include JB/setup %}

<br/><br/><br/>

## {{ site.data.project.name }} Community

Every volunteer project obtains its strength from the people involved in it. We invite you to participate as much or as little as you choose.

You can:

* Use our project and provide a feedback.
* Provide us with the use-cases.
* Report bugs and submit patches.
* Contribute code, javadocs, documentation.

Visit the [Contributing](https://cwiki.apache.org/confluence/display/RYA/Contributing+to+Rya) page for more information.

### Mailing list

Get help using {{ site.data.project.short_name }} or contribute to the project on our mailing lists:

{% if site.data.project.user_list %}
* [site.data.project.user_list](mailto:{{ site.data.project.user_list }}) is for usage questions, help, and announcements. [subscribe](mailto:{{ site.data.project.user_list_subscribe }}?subject=send this email to subscribe),     [unsubscribe](mailto:{{ site.data.project.dev_list_unsubscribe }}?subject=send this email to unsubscribe), [archives]({{ site.data.project.user_list_archive_mailarchive }})
{% endif %}
* [{{ site.data.project.dev_list }}](mailto:{{ site.data.project.dev_list }}) is for useage questions, help, and people who want to contribute code to {{ site.data.project.short_name }}. [subscribe](mailto:{{ site.data.project.dev_list_subscribe }}?subject=send this email to subscribe), [unsubscribe](mailto:{{ site.data.project.dev_list_unsubscribe }}?subject=send this email to unsubscribe), [archives]({{ site.data.project.dev_list_archive_mailarchive }})
* [{{ site.data.project.commits_list }}](mailto:{{ site.data.project.commits_list }}) is for commit messages to {{ site.data.project.short_name }}. [subscribe](mailto:{{ site.data.project.commits_list_subscribe }}?subject=send this email to subscribe), [unsubscribe](mailto:{{ site.data.project.commits_list_unsubscribe }}?subject=send this email to unsubscribe), [archives]({{ site.data.project.commits_list_archive_mailarchive }})
{% if site.data.project.notifications_list %}
* [{{ site.data.project.notifications_list }}](mailto:{{ site.data.project.notifications_list }}) is for automated messages from JIRA, continuous integration, etc. for {{ site.data.project.short_name }}. [subscribe](mailto:{{ site.data.project.notifications_list_subscribe }}?subject=send this email to subscribe), [unsubscribe](mailto:{{ site.data.project.notifications_list_unsubscribe }}?subject=send this email to unsubscribe), [archives]({{ site.data.project.notifications_list_archive_mailarchive }})
{% endif %}

### Issue tracker


#### Bug Reports

Found a bug? Enter an issue in the [Issue Tracker](https://issues.apache.org/jira/browse/{{ site.data.project.jira }}).

Before submitting an issue, please:

* Verify that the bug does in fact exist.
* Search the issue tracker to verify there is no existing issue reporting the bug you've found.
* Consider tracking down the bug yourself in the {{ site.data.project.short_name}}'s source and submitting a pull request along with your bug report. This is a great time saver for the {{ site.data.project.short_name }}  developers and helps ensure the bug will be fixed quickly.



#### Feature Requests

Enhancement requests for new features are also welcome. The more concrete and rationale the request is, the greater the chance it will incorporated into future releases.


  [https://issues.apache.org/jira/browse/{{ site.data.project.jira }}](https://issues.apache.org/jira/browse/{{ site.data.project.jira }})


### Source Code

The project sources are accessible via the [source code repository]({{ site.data.project.source_repository }}) which is also mirrored in [GitHub]({{ site.data.project.source_repository_mirror }})


### Website Source Code

The project website sources are accessible via the [website source code repository]({{ site.data.project.website_repository }}).
