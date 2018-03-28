[![Build Status](https://travis-ci.org/bardsoftware/papeeria-compiler-fleet.svg?branch=master)](https://travis-ci.org/bardsoftware/papeeria-compiler-fleet)

## Summary

This is an experimental component of Papeeria which is responsible for processing compiling tasks asynchronously in producer-consumer manner. It is not restricted to any particular compiler, but we're going to implement RMarkdown compiler as a pilot. 

The main functions of this component are as follows:

* Run a fleet of compiler nodes which take tasks from some message queue, e.g. from PubSub
* Compile task on the node 
* Publish the result of compilation back to the message queue

This project was started in March 2018 as a term practice work of the students of Saint-Petersburg Academic University.

## Policy of making changes

Branch `master` contains code which is considered to be production-ready, that is, at any moment we may decide to build the code from master and push it to production. It must be compilable, it must be operational, it should be tested as much as possible.

If you want to add a new awesome feature, please follow the policy of making changes:

1. **Create a ticket** in the issue tracker and briefly describe the summary of changes.
1. **Create a branch** forked from `master` and named `tkt-ISSUE_NUMBER-MNEMONIC_NAME` where `ISSUE_NUMBER` is the number of issue created on the previous setp and `MNEMONIC_NAME` is short mnemonic name for managing the khaos of branches. Example: `tkt-1-proto-file`
1. Commit your **changes to the branch**. When you get something that compiles, works as intended (or vice versa, is not involved in the operational process at all) and doesn't break the existing functionality, **create a pull request** and wait for **code review**. The smaller is the number of changes the better. As a rule of thumb, changing up to 500 lines of code is okay, up to 1000 is acceptable. If you changed more than 1000 lines of code then you probably made something wrong, either from coding of from change process perspective and you should consider refactoring your code and breaking it into smaller pieces.
    * It is not required that pull request completely implements what is written in the ticket. 
    * It is okay to create many sequential pull requests per single ticket
    * It is okay to send pull request with no unit tests, however, we appreciate writing "tests are coming" in the pull request summary if you plan to add them later
1. Once code review is started, please **do not add anything** besides unit tests and changes requested by reviewers to your branch. If you add some 500 LOC in addition to already reviewed then review essentially starts from scratch and the process never converges. We want `tkt-` branches to be merged as soon as possible.
1. You will get a lot of comments in the review. Please **reply to every comment** with either single word "Done" if you agree with the comment and made the requested changes or with something more verbose. Replying is a way to ensure both yourself and reviewer that you don't miss or didn't ignore any comment.
1. There are so called "[pending reviews](https://help.github.com/articles/reviewing-proposed-changes-in-a-pull-request/)" in GitHub which is a great way to write comments without sending them and send the batch of comments later. This way you can read the review comments, change your code, comment "done", proceed to the next comment and once you are done, commit changes in the code, push and then send your replies. It makes commenting meaningful and less spammy: reviewer will receive a single email and he will be sure that everything which is "done" is actually done and sits in the repository.
