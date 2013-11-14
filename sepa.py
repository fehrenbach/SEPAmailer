#! /usr/bin/env python2
# -*- coding: utf8

import argparse
from string import Template
import csv
from smtplib import SMTP
import datetime
import getpass
from email.mime.text import MIMEText


parser = argparse.ArgumentParser(description='Send SEPA mails')

parser.add_argument("-v", "--verbose", help="Print lots of stuff, including the actual emails to be sent.",
                    action="store_true")
parser.add_argument("--dry-run", help="Do not actually send mails. Implies --verbose.",
                    action="store_true")
parser.add_argument("--template", required=True, type=file)
parser.add_argument("--csv", help="CSV file with recipients mail addresses and template parameters.",
                    required=True, type=argparse.FileType('rU'))
parser.add_argument("--csv-delimiter", type=str, default=';',
                    help="Character that separates columns in the CSV file. Defaults to ';'.")
parser.add_argument("--csv-mail-column", type=str, default='EMail',
                    help="Column in the CSV file that contains the e-mail address. Defaults to 'EMail'.")
parser.add_argument("--mail-subject", type=str, default='SEPA',
                    help="Subject line for e-mails.")
parser.add_argument("--mail-from", type=str,
                    default='"Universitätschor Marburg e.V. - Finanzen" <finanzen@unichor-marburg.de>',
                    help='From name and address for e-mails. Format: "Pretty name" <mail@address.domain>.')
parser.add_argument("--smtp-server", type=str, required=True)
parser.add_argument("--smtp-user", type=str, required=True)

args = parser.parse_args()
# --dry-run implies --verbose
if args.dry_run:
    args.verbose = True

if args.verbose:
    print args


## Read template file into template object
template = Template(args.template.read())


## SMTP connection
smtp = SMTP()
if not args.dry_run:
    smtp.connect(args.smtp_server)
    smtp.ehlo()
    smtp.starttls()
    smtp.ehlo()
    smtp.login(args.smtp_user, getpass.getpass("Password for {0}@{1}:".format(args.smtp_user, args.smtp_server)))


## Read CSV, instantiate template, and actually send mail
firstRow = True
mapping = {}
for row in csv.reader(args.csv, delimiter=args.csv_delimiter):
    if firstRow:
        firstRow = False
        i = 0
        for column in row:
            mapping[column] = i
            i = i+1
        if args.verbose:
            print 'Template parameters and mapping to columns:'
            print mapping
    else:
        templateValues = {key: row[column] for key, column in mapping.items()}
        if args.verbose:
            print templateValues
        if not templateValues[args.csv_mail_column].strip():
            print 'Warning! Mail address is not set. Skipping this row and continuing with next row. Full row:'
            print templateValues
            continue
        content = template.substitute(templateValues)
        mail = MIMEText(content)
        mail['Subject'] = args.mail_subject
        mail['From'] = args.mail_from
        mail['To'] = templateValues[args.csv_mail_column]
        if args.verbose:
            print mail
        if not args.dry_run:
            try:
                smtp.sendmail(args.mail_from, templateValues[args.csv_mail_column], mail.as_string())
            except Exception as e:
                print 'ERROR! Sending mail failed for some reason. Continuing with next row.'
                print 'Exception:'
                print e
                print 'Full row data:'
                print templateValues

if not args.dry_run: smtp.close()
