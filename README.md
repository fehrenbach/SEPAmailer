SEPAmailer
==========

Download the jar here: https://github.com/fehrenbach/SEPAmailer/releases/tag/v0.0.1

Example usage:

```
java -jar sepamailer-0.0.1.jar --template test.template --csv test.csv --csv-delimiter=";" --mail-subject "hello world" --smtp-user 'stefan.fehrenbach@gmail.com' --smtp-server smtp.gmail.com --mail-from 'stefan.fehrenbach@gmail.com' --attachment test.pdf
```

Template syntax is explained here: https://github.com/fhd/clostache

The data format is just the names of the columns, as indicated in the first line of the CSV file.

Conditionals do not yet work as intended, see the comment in `test.template`.
