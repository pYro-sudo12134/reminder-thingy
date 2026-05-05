import { Button, Container, Text, Title } from '@mantine/core';
import { Dots } from './Dots';
import classes from './HeroText.module.css';
import Link from 'next/link';

export function HeroText() {
  return (
    <Container className={classes.wrapper} size={1400}>
      <Dots className={classes.dotsLeft} />
      <Dots className={classes.dotsRight} />

      <div className={classes.inner}>
        <Title className={classes.title}>
          Welcome to the{' '}
          <Text component="span" className={classes.highlight} inherit>
            Reminder-Thingy
          </Text>
        </Title>

        <Container p={0} size={600}>
          <Text size="lg" c="dimmed" className={classes.description}>
            Always be on time, manage your events like you never did.
          </Text>
        </Container>

        <div className={classes.controls}>
          <Link href="/about" passHref>
          <Button className={classes.control} size="lg" variant="default" color="gray">
            About Us
          </Button>
          </Link>
          <Link href="/reminders" passHref>
            <Button className={classes.control} size="lg" style={{ marginLeft: '15px' }}>
              Manage Reminders
            </Button>
          </Link>
        </div>
      </div>
    </Container>
  );
}
