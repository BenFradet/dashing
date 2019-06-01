# Dashing

Dashboards to monitor the health of your open source organization.

[![Build Status](https://travis-ci.org/BenFradet/dashing.svg?branch=master)](https://travis-ci.org/BenFradet/dashing)

## Dashboards

As a first step, this project incorporates the following set of dashboards:

### Hero repo stars

The evolution of the number of stars of your "hero" repository as a function of time:

![hero-repo](https://github.com/BenFradet/dashing/raw/master/screenshots/hero_repo_stars.png)

### Other repos stars

The evolution of the number of stars of the top 5 repositories inside the organization (excluding
the hero repo) as a function of time:

![topn-repos](https://github.com/BenFradet/dashing/raw/master/screenshots/top_n_repos_stars.png)

### Number of pull request opened

The number of open pull requests created by people outside the organization as a function
of time quarterly:

![quarterly-prs](https://github.com/BenFradet/dashing/raw/master/screenshots/prs-quarterly.png)

There is also a dashboard who groups opened PRs by month:

![monthly-prs](https://github.com/BenFradet/dashing/raw/master/screenshots/prs-monthly.png)

## How to use it

If you want to use it for your own organization, you can pass a custom
[`application.conf`](server/src/main/resources/reference.conf):

```ini
# Github access token
ghToken = token

# Configuration for the dashboards about pull requests
prDashboards = {
  # Github organizations from which to retrieve the data regarding pull requests
  orgs = [ snowplow ]
  # List of github handles to ignore, for example if they're wrongly not part of the org
  peopleToIgnore = [ "scala-steward" ]
  # How far in the past the GitHub pull request data lookups should go
  lookback = 730 days
}

# Configuration for the dashboards about stars
starDashboards = {
  # Github organization from which to retrieve the data regarding stars
  org = snowplow
  # Name of the most popular repository for the hero repo dashboard
  heroRepo = snowplow
  # Number of most popular repositories to analyze (hero repo excluded)
  topNRepos = 5
}

# Duration for which data from GitHub is cached
cacheDuration = 12 hours

# Host the server should bind to
host = localhost

# Port the server should bind to
port = 8080
```
