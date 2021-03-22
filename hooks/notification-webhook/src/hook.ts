/**
Copyright 2020 iteratec GmbH

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
import { isMatch } from "lodash";
import { Finding } from "./model/Finding";
import { NotificationChannel } from "./model/NotificationChannel";
import { Notifier } from "./Notifier";
import { NotifierFactory } from "./NotifierFactory";

const NOTIFICATION_CHANNELS = "/Some/Path/to/file";

export async function handle({ getFindings, scan }) {
  let findings: Finding[] = getFindings();
  let notificationChannels: NotificationChannel[] = JSON.parse(process.env["NOTIFICATIONS"])
  for (const channel of notificationChannels) {
    const findingsToNotify = findings.filter(finding => matches(finding, channel.rules));
    const notifier: Notifier = NotifierFactory.create(channel, scan, findingsToNotify);
    await notifier.sendMessage();
  }
}

export function matches(finding: Finding, rules: any): boolean {
  for (let rule of rules) {
    if (doesNotMatch(rule, finding)) return false;
  }
  return true;
}

function doesNotMatch(rule: any, finding: Finding): boolean {
  return !rule.matches.anyOf.some((condition: object) => isMatch(finding, condition));
}
