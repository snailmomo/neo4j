/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.index.internal.gbptree;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.neo4j.helpers.Exceptions;
import org.neo4j.scheduler.JobSchedulerAdapter;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GroupingRecoveryCleanupWorkCollectorTest
{
    private final ImmediateJobScheduler jobScheduler = new ImmediateJobScheduler();
    private final GroupingRecoveryCleanupWorkCollector collector =
            new GroupingRecoveryCleanupWorkCollector( jobScheduler );

    @Test
    public void mustNotScheduleAnyJobsBeforeStart()
    {
        // given
        List<CleanupJob> allRuns = new ArrayList<>();
        List<CleanupJob> expectedJobs = someJobs( allRuns );

        // when
        collector.init();
        addAll( expectedJobs );

        // then
        assertTrue( allRuns.isEmpty() );
    }

    @Test
    public void mustScheduleAllJobs()
    {
        // given
        List<CleanupJob> allRuns = new ArrayList<>();
        List<CleanupJob> expectedJobs = someJobs( allRuns );

        // when
        collector.init();
        addAll( expectedJobs );
        collector.start();

        // then
        assertSame( expectedJobs, allRuns );
    }

    @Test
    public void mustNotScheduleOldJobsAfterRestart()
    {
        // given
        List<CleanupJob> allRuns = new ArrayList<>();
        List<CleanupJob> someJobs = someJobs( allRuns );

        // when
        collector.init();
        addAll( someJobs );
        collector.init();
        collector.start();

        // then
        assertTrue( allRuns.isEmpty() );
    }

    @Test
    public void mustNotScheduleOldJobsOnMultipleStart()
    {
        // given
        List<CleanupJob> allRuns = new ArrayList<>();
        List<CleanupJob> expectedJobs = someJobs( allRuns );

        // when
        collector.init();
        addAll( expectedJobs );
        collector.start();
        collector.start();

        // then
        assertSame( expectedJobs, allRuns );
    }

    @Test
    public void mustNotScheduleOldJobsOnStartStopStart() throws Throwable
    {
        // given
        List<CleanupJob> allRuns = new ArrayList<>();
        List<CleanupJob> expectedJobs = someJobs( allRuns );

        // when
        collector.init();
        addAll( expectedJobs );
        collector.start();
        collector.stop();
        collector.start();

        // then
        assertSame( expectedJobs, allRuns );
    }

    @Test
    public void executeAllTheJobsWhenSeparateJobFails()
    {
        List<CleanupJob> allRuns = new ArrayList<>();
        collector.init();

        DummyJob firstJob = new DummyJob( "first", allRuns );
        DummyJob thirdJob = new DummyJob( "third", allRuns );
        DummyJob fourthJob = new DummyJob( "fourth", allRuns );
        List<CleanupJob> expectedJobs = Arrays.asList( firstJob, thirdJob, fourthJob );

        collector.add( firstJob );
        collector.add( new EvilJob() );
        collector.add( thirdJob );
        collector.add( fourthJob );

        try
        {
            collector.start();
            fail( "One of the jobs throws exception." );
        }
        catch ( RuntimeException e )
        {
            assertTrue( Exceptions.contains( e, "Resilient to run attempts", RuntimeException.class ) );
        }

        assertSame( expectedJobs, allRuns );
    }

    @Test
    public void throwOnAddingJobsAfterStart()
    {
        collector.init();
        collector.start();

        try
        {
            collector.add( new DummyJob( "first", new ArrayList<>() ) );
            fail( "Collector should not acccept new jobs after start." );
        }
        catch ( IllegalStateException ise )
        {
            // expected
        }
    }

    private void addAll( Collection<CleanupJob> jobs )
    {
        jobs.forEach( collector::add );
    }

    private void assertSame( List<CleanupJob> someJobs, List<CleanupJob> actual )
    {
        assertTrue( actual.containsAll( someJobs ) );
        assertTrue( someJobs.containsAll( actual ) );
    }

    private List<CleanupJob> someJobs( List<CleanupJob> allRuns )
    {
        return new ArrayList<>( Arrays.asList(
                new DummyJob( "A", allRuns ),
                new DummyJob( "B", allRuns ),
                new DummyJob( "C", allRuns )
        ) );
    }

    private class ImmediateJobScheduler extends JobSchedulerAdapter
    {
        @Override
        public JobHandle schedule( Group group, Runnable job )
        {
            job.run();
            return super.schedule( group, job );
        }
    }

    private class EvilJob implements CleanupJob
    {

        @Override
        public boolean needed()
        {
            return false;
        }

        @Override
        public boolean hasFailed()
        {
            return false;
        }

        @Override
        public Exception getCause()
        {
            return null;
        }

        @Override
        public void close()
        {
            // nothing to close
        }

        @Override
        public void run()
        {
            throw new RuntimeException( "Resilient to run attempts" );
        }
    }

    private class DummyJob implements CleanupJob
    {
        private final String name;
        private final List<CleanupJob> allRuns;

        DummyJob( String name, List<CleanupJob> allRuns )
        {
            this.name = name;
            this.allRuns = allRuns;
        }

        @Override
        public String toString()
        {
            return name;
        }

        @Override
        public boolean needed()
        {
            return false;
        }

        @Override
        public boolean hasFailed()
        {
            return false;
        }

        @Override
        public Exception getCause()
        {
            return null;
        }

        @Override
        public void close()
        {   // no-op
        }

        @Override
        public void run()
        {
            allRuns.add( this );
        }
    }
}
